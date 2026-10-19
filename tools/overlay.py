import sys,re,zipfile,os
src,out=sys.argv[1],sys.argv[2]
want=[l.strip() for l in open(sys.argv[3]) if l.strip() and not l.startswith('#')]
z=zipfile.ZipFile(src)
nm=z.namelist()
ren=[(r'\bjava\.awt\b','jawt'),(r'\bsun\.awt\.geom\b','jsun.geom'),(r'\bjavax\.imageio\b','jimageio'),(r'\bjavax\.swing\b','jswing'),(r'\bjava\.beans\b','jbeans')]
def put(n,t):
    for a,b in ren: t=re.sub(a,b,t)
    t=re.sub(r'^\s*@ConstructorProperties.*$','',t,flags=re.M)
    t=re.sub(r'^import\s+jbeans\.ConstructorProperties;','',t,flags=re.M)
    t=t.replace('jdk.internal.util.ArraysSupport.newLength','jsun.geom.ArrSup.newLength').replace('ArraysSupport.newLength','jsun.geom.ArrSup.newLength')
    t=re.sub(r'^import jdk\.internal\.util\.ArraysSupport;','',t,flags=re.M)
    for a,b in ren: n=re.sub(a.replace(r'\b','').replace('\\.','/'),b,n) if False else n
    t=re.sub(r'\bnon-sealed\s+','',t)
    t=re.sub(r'\bsealed\s+','',t)
    t=re.sub(r'\bpermits\s+[\w.,\s]+?(?=\{)','',t)
    t=re.sub(r'^import jbeans\.Transient;','',t,flags=re.M)
    t=re.sub(r'^\s*@Transient\s*$','',t,flags=re.M)
    t=re.sub(r'static\s*\{\s*(/\*.*?\*/\s*)?Toolkit\.loadLibraries\(\);[^{}]*(\{[^{}]*\})?[^{}]*\}','',t,flags=re.S)
    t=re.sub(r'(private|public)?\s*static\s+native\s+void\s+initIDs\(\);','',t)
    p=os.path.join(out,n)
    os.makedirs(os.path.dirname(p),exist_ok=True)
    open(p,'w').write(t)
n=0
for w in want:
    for e in nm:
        if not e.endswith('.java'): continue
        r=e.split('/',1)[1] if e.count('/') else e
        if r==w or (w.endswith('/*') and r.startswith(w[:-1]) and '/' not in r[len(w)-1:]):
            d=r.replace('java/awt','jawt').replace('sun/awt/geom','jsun/geom').replace('javax/imageio','jimageio')
            put(d,z.read(e).decode('utf8'))
            n+=1
os.makedirs(os.path.join(out,'jsun/geom'),exist_ok=True)
open(os.path.join(out,'jsun/geom/ArrSup.java'),'w').write('package jsun.geom;\npublic class ArrSup {\n    public static int newLength(int o,int min,int pref){\n        int l=o+Math.max(min,pref);\n        if(l<0) throw new OutOfMemoryError();\n        return l;\n    }\n}\n')
print(n,'real classes overlaid')
