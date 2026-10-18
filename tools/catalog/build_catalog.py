"""Build a reproducible offline catalog from original public datasets."""
import json, math, hashlib, datetime
from pathlib import Path

def number(value, maximum=1000):
    try:
        n=float(value)
        return n if math.isfinite(n) and 0<=n<=maximum else None
    except (TypeError, ValueError): return None

def normalize_record(record, source):
    if source=='usda':
        nutrients={str(n.get('nutrient',{}).get('id')):n.get('amount') for n in record.get('foodNutrients',[])}
        kcal=number(nutrients.get('1008'))
        values=[kcal]+[number(nutrients.get(k),100) for k in ('1003','1004','1005')]
        name=record.get('description','').strip(); ident=str(record.get('fdcId',''))
        portions=[]
        for p in record.get('foodPortions',[]):
            mass=number(p.get('gramWeight'),100000)
            amount=number(p.get('amount'),10000)
            if mass and amount:
                portions.append({'name':p.get('modifier') or p.get('portionDescription') or 'Порция из источника', 'grams':mass/amount})
        aliases=[]
    else:
        n=record.get('nutriments',{})
        kcal=number(n.get('energy-kcal_100g'))
        if kcal is None and 'energy-kcal_100g' not in n and n.get('energy_unit','').lower()=='kj':
            kj=number(n.get('energy_100g'),4184)
            if kj is not None:kcal=kj/4.184
        values=[kcal]+[number(n.get(k+'_100g'),100) for k in ('proteins','fat','carbohydrates')]
        name=(record.get('product_name_ru') or record.get('product_name') or '').strip()
        if record.get('brands'):name+=' · '+record['brands']
        ident=str(record.get('code',''));portions=[];aliases=[]
        # OFF values may be per 100 ml: without an explicit mass basis do not pretend they are per 100 g.
        if record.get('nutrition_data_per') not in (None,'100g'):return None
    if not name or not ident or values[0] is None:return None
    return {'id':source+':'+ident,'name':name,'aliases':aliases,'per100g':dict(zip(('kcal','protein','fat','carbs'),values)), 'portions':portions,'density':None,'source':('USDA FoodData Central · '+ident if source=='usda' else 'Open Food Facts · '+ident),'isWater':False}

def build_catalog(inputs, output):
    foods={}; rejected=0; hashes={}
    for p in inputs:
        p=Path(p);data=json.loads(p.read_text(encoding='utf-8'));hashes[p.name]=hashlib.sha256(p.read_bytes()).hexdigest()
        source='off' if 'products' in data else 'usda'
        records=data.get('products',[]) if source=='off' else next(iter(data.values()))
        for record in records:
            food=normalize_record(record,source)
            if food:foods[food['id']]=food
            else:rejected+=1
    alias_path=Path(__file__).with_name('aliases.json')
    aliases=json.loads(alias_path.read_text(encoding='utf-8')) if alias_path.exists() else {}
    for ident,local in aliases.items():
        if ident in foods:
            f=foods[ident];f['aliases']=[f['name']]+local.get('aliases',[]);f['name']=local['name']
            if 'portions' in local:f['portions']=local['portions']
            if 'density' in local:f['density']=local['density']
            if 'isWater' in local:f['isWater']=local['isWater']
    ordered=sorted(foods.values(),key=lambda f:(f['id'] not in aliases,f['name']))
    output=Path(output);output.parent.mkdir(parents=True,exist_ok=True)
    output.write_text(json.dumps(ordered,ensure_ascii=False,separators=(',',':')),encoding='utf-8')
    report={'retrieved':'2026-10-18','count':len(foods),'localized':sum(i in foods for i in aliases),'excluded_missing_or_invalid_data':rejected,'sources':{s:sum(f['id'].startswith(s+':') for f in ordered) for s in ('usda','off')},'input_sha256':hashes,'catalog_sha256':hashlib.sha256(output.read_bytes()).hexdigest()}
    output.with_name('report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
    return report

if __name__=='__main__':
    import sys
    print(json.dumps(build_catalog([Path(p) for p in sys.argv[1:]],Path('app/src/main/assets/catalog/foods.json')),indent=2))
