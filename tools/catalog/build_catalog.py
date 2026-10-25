"""Build a reproducible offline catalog from original public datasets.

Every food carries a Russian `name` and an English `nameEn`; USDA descriptions are the English originals and
tools/catalog/i18n holds their reviewed Russian translations. Nutrition values come from the sources unchanged;
extra-foods.json adds estimated dishes whose derivation is recorded in the item itself.
"""
import json, math, hashlib, re
from pathlib import Path

HERE=Path(__file__).parent

def number(value, maximum=1000):
    try:
        n=float(value)
        return n if math.isfinite(n) and 0<=n<=maximum else None
    except (TypeError, ValueError): return None

def fiber_or_zero(fiber, carbs):
    # Fibre is part of carbohydrate by difference, so a food without carbohydrate has none.
    return 0.0 if fiber is None and carbs==0 else fiber

def normalize_record(record, source):
    if source=='usda':
        nutrients={str(n.get('nutrient',{}).get('id')):n.get('amount') for n in record.get('foodNutrients',[])}
        kcal=number(nutrients.get('1008'))
        values=[kcal]+[number(nutrients.get(k),100) for k in ('1003','1004','1005','1079')]
        name=record.get('description','').strip(); ident=str(record.get('fdcId',''))
        portions=[]
        for p in record.get('foodPortions',[]):
            mass=number(p.get('gramWeight'),100000)
            amount=number(p.get('amount'),10000)
            if mass and amount:
                portions.append({'name':(p.get('modifier') or p.get('portionDescription') or '').strip() or 'serving', 'grams':mass/amount})
        aliases=[]
    else:
        n=record.get('nutriments',{})
        kcal=number(n.get('energy-kcal_100g'))
        if kcal is None and 'energy-kcal_100g' not in n and n.get('energy_unit','').lower()=='kj':
            kj=number(n.get('energy_100g'),4184)
            if kj is not None:kcal=kj/4.184
        values=[kcal]+[number(n.get(k+'_100g'),100) for k in ('proteins','fat','carbohydrates','fiber')]
        name=(record.get('product_name_ru') or record.get('product_name') or '').strip()
        if record.get('brands'):name+=' · '+record['brands']
        ident=str(record.get('code',''));portions=[];aliases=[]
        # OFF values may be per 100 ml: without an explicit mass basis do not pretend they are per 100 g.
        if record.get('nutrition_data_per') not in (None,'100g'):return None
    if not name or not ident or values[0] is None:return None
    values[4]=fiber_or_zero(values[4],values[3])
    return {'id':source+':'+ident,'name':name,'aliases':aliases,'per100g':dict(zip(('kcal','protein','fat','carbs','fiber'),values)), 'portions':portions,'density':None,'source':('USDA FoodData Central · '+ident if source=='usda' else 'Open Food Facts · '+ident),'isWater':False}

def extra_food(item, foods):
    """Estimated dish: kcal, protein and fat for a known weight; carbohydrate and fibre are derived as the item says."""
    food={'id':item['id'],'name':item['name'],'nameEn':item['nameEn'],'aliases':item.get('aliases',[]),'portions':item.get('portions',[]),
          'density':item.get('density'),'source':item['source'],'sourceEn':item.get('sourceEn'),'isWater':False,'common':True}
    if 'base' in item:
        food['per100g']=dict(foods[item['base']]['per100g']);return food
    b=item['basis']
    def usda(ident,key):
        value=foods[ident]['per100g'][key]
        if value is None:raise ValueError(f'{item["id"]}: {ident} has no {key}')
        return value
    if 'ingredients' in b:
        # A portion modelled from USDA ingredients. A nutrient given as a number is kept (e.g. the user's estimate),
        # "remainder" fat is the Atwater energy left after protein and carbohydrate, everything else is the sum.
        ing=b['ingredients'];grams=b.get('grams') or sum(g for _,g in ing)
        n={key:b[key] if isinstance(b.get(key),(int,float)) else sum(usda(i,key)*g/100 for i,g in ing) for key in ('kcal','protein','fat','carbs','fiber')}
        if b.get('fat')=='remainder':
            n['fat']=(n['kcal']-4*n['protein']-4*n['carbs'])/9
            if n['fat']<0:raise ValueError(f'{item["id"]}: protein and carbohydrate exceed the energy')
        k=100/grams
        food['per100g']={key:round(v*k,2) for key,v in n.items()}
        return food
    c=b['carbs']
    if 'ingredients' in c:
        carbs=sum(usda(i,'carbs')*g/100 for i,g in c['ingredients']);fiber=sum(usda(i,'fiber')*g/100 for i,g in c['ingredients'])
    else:
        # Atwater general factors: what energy protein and fat do not explain is available carbohydrate.
        available=(b['kcal']-4*b['protein']-9*b['fat'])/4
        if available<0:raise ValueError(f'{item["id"]}: protein and fat exceed the energy')
        if 'fiber' in c:fiber=c['fiber']
        elif 'fiberPer100Of' in c:fiber=usda(c['fiberPer100Of'],'fiber')*b['grams']/100
        else:
            ref=c['fiberRatioOf'];fiber=available*usda(ref,'fiber')/(usda(ref,'carbs')-usda(ref,'fiber'))
        carbs=available+fiber
    k=100/b['grams']
    food['per100g']={'kcal':round(b['kcal']*k,2),'protein':round(b['protein']*k,2),'fat':round(b['fat']*k,2),'carbs':round(carbs*k,2),'fiber':round(fiber*k,2)}
    return food

def load_json(path,default):
    return json.loads(path.read_text(encoding='utf-8')) if path.exists() else default

def build_catalog(inputs, output, translations=HERE/'i18n'):
    foods={}; rejected=0; hashes={}
    for p in inputs:
        p=Path(p);data=json.loads(p.read_text(encoding='utf-8'));hashes[p.name]=hashlib.sha256(p.read_bytes()).hexdigest()
        source='off' if 'products' in data else 'usda'
        records=data.get('products',[]) if source=='off' else next(iter(data.values()))
        for record in records:
            food=normalize_record(record,source)
            if food:foods[food['id']]=food
            else:rejected+=1
    aliases=load_json(HERE/'aliases.json',{})
    names_ru=load_json(translations/'food-names-ru.json',{})
    portions_ru=load_json(translations/'portion-names-ru.json',{})
    missing=[]
    for f in foods.values():
        if not f['id'].startswith('usda:'):continue
        local=aliases.get(f['id'],{})
        f['nameEn']=f['name'];f['name']=local.get('name') or names_ru.get(f['id'])
        if not f['name']:missing.append(f['id'])
        f['aliases']=list(local.get('aliases',[]))
        f['portions']=[{'name':portions_ru.get(p['name']),'nameEn':p['name'],'grams':p['grams']} for p in f['portions']]
        missing+=[f['id']+' portion '+p['nameEn'] for p in f['portions'] if not p['name']]
        for key in ('portions','density','isWater'):
            if key in local:f[key]=local[key]
        f['common']=f['id'] in aliases
    if missing:raise ValueError(f'{len(missing)} untranslated names, e.g. {missing[:5]}')
    for item in load_json(HERE/'extra-foods.json',[])+load_json(HERE/'sushi-foods.json',[]):
        foods[item['id']]=extra_food(item,foods)
    search_aliases=load_json(HERE/'search-aliases.json',{})
    for f in foods.values():
        original=' '.join([f.get('nameEn') or '',f['name']]+f['aliases']).lower()
        extra=[value for key,value in search_aliases.items() if re.search(r'\b'+re.escape(key)+r'\b',original)]
        f['aliases']+=extra
    ordered=sorted(foods.values(),key=lambda f:(not f.get('common'),f['name']))
    output=Path(output);output.parent.mkdir(parents=True,exist_ok=True)
    output.write_text(json.dumps(ordered,ensure_ascii=False,separators=(',',':')),encoding='utf-8')
    report={'retrieved':'2026-10-18','count':len(foods),'localized':sum(i in foods for i in aliases),'translated':sum(i in foods for i in names_ru),
            'estimated_dishes':sum(1 for f in foods.values() if f['id'].startswith('local:')),'with_fiber':sum(f['per100g']['fiber'] is not None for f in foods.values()),
            'excluded_missing_or_invalid_data':rejected,'sources':{s:sum(f['id'].startswith(s+':') for f in ordered) for s in ('usda','off','local')},'input_sha256':hashes,'catalog_sha256':hashlib.sha256(output.read_bytes()).hexdigest()}
    output.with_name('report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
    return report

if __name__=='__main__':
    import sys
    print(json.dumps(build_catalog([Path(p) for p in sys.argv[1:]],Path('app/src/main/assets/catalog/foods.json')),indent=2))
