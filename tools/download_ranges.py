"""Download a public SDK/archive in bounded HTTP ranges to avoid stalled long transfers."""
import concurrent.futures, pathlib, urllib.request, sys, hashlib
url, dest, total = sys.argv[1], pathlib.Path(sys.argv[2]), int(sys.argv[3])
dest.parent.mkdir(parents=True,exist_ok=True)
step=4*1024*1024
with dest.open('wb') as f:f.truncate(total)
def chunk(start):
    end=min(total,start+step)-1
    for attempt in range(4):
        try:
            request=urllib.request.Request(url,headers={'Range':f'bytes={start}-{end}'})
            with urllib.request.urlopen(request,timeout=40) as r:
                assert r.status==206
                b=r.read()
            assert len(b)==end-start+1
            with dest.open('r+b') as f:f.seek(start);f.write(b)
            return len(b)
        except Exception:
            if attempt==3:raise
with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
    done=0
    for count in pool.map(chunk,range(0,total,step)):
        done+=count
        if done%(step*16)==0:print(f'{done}/{total}',flush=True)
print('Downloaded',dest,total,flush=True)
