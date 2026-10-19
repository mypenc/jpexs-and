import java.io.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;

public class ShimGen {
    static final String[][] PK={{"java.awt.","jawt."},{"javax.imageio.","jimageio."},{"javax.swing.","jswing."},{"javax.accessibility.","jacc."},{"java.beans.","jbeans."}};
    static Set<String> wl=new LinkedHashSet<>();
    static Path out;

    static String mp(String n){
        for(String[] p:PK) if(n.startsWith(p[0])) return p[1]+n.substring(p[0].length());
        return n;
    }
    static boolean shimmed(String n){
        for(String[] p:PK) if(n.startsWith(p[0])) return true;
        return n.startsWith("sun.")||n.startsWith("com.sun.");
    }
    static String top(Class<?> c){
        while(c.getDeclaringClass()!=null) c=c.getDeclaringClass();
        return c.getName();
    }
    static boolean ok(Class<?> c){
        while(c.isArray()) c=c.getComponentType();
        if(c.isPrimitive()) return true;
        if(!shimmed(c.getName())) return true;
        return wl.contains(top(c));
    }
    static String ty(Type t){
        if(t instanceof Class){
            Class<?> c=(Class<?>)t;
            if(!ok(c)) return null;
            if(c.isArray()){
                String e=ty(c.getComponentType());
                return e==null?null:e+"[]";
            }
            return mp(c.getName()).replace('$','.');
        }
        if(t instanceof ParameterizedType){
            ParameterizedType p=(ParameterizedType)t;
            String r=ty(p.getRawType());
            if(r==null) return null;
            StringBuilder b=new StringBuilder(r).append('<');
            Type[] a=p.getActualTypeArguments();
            for(int i=0;i<a.length;i++){
                String s=ty(a[i]);
                if(s==null) return null;
                b.append(i>0?",":"").append(s);
            }
            return b.append('>').toString();
        }
        if(t instanceof GenericArrayType){
            String e=ty(((GenericArrayType)t).getGenericComponentType());
            return e==null?null:e+"[]";
        }
        if(t instanceof TypeVariable) return ((TypeVariable<?>)t).getName();
        if(t instanceof WildcardType){
            WildcardType w=(WildcardType)t;
            if(w.getLowerBounds().length>0){
                String s=ty(w.getLowerBounds()[0]);
                return s==null?null:"? super "+s;
            }
            Type u=w.getUpperBounds()[0];
            if(u==Object.class) return "?";
            String s=ty(u);
            return s==null?null:"? extends "+s;
        }
        return null;
    }
    static String tps(TypeVariable<?>[] v){
        if(v.length==0) return "";
        StringBuilder b=new StringBuilder("<");
        for(int i=0;i<v.length;i++){
            b.append(i>0?",":"").append(v[i].getName());
            List<String> bs=new ArrayList<>();
            for(Type x:v[i].getBounds()){
                if(x==Object.class) continue;
                String s=ty(x);
                if(s!=null) bs.add(s);
            }
            if(!bs.isEmpty()) b.append(" extends ").append(String.join("&",bs));
        }
        return b.append('>').toString();
    }
    static String dflt(Class<?> c){
        if(c==boolean.class) return "false";
        if(c.isPrimitive()) return "("+c.getName()+")0";
        return "("+erased(c)+")null";
    }
    static String erased(Class<?> c){
        if(c.isArray()) return erased(c.getComponentType())+"[]";
        return mp(c.getName()).replace('$','.');
    }
    static String mods(int m,boolean iface){
        StringBuilder b=new StringBuilder();
        if(Modifier.isPublic(m)) b.append("public ");
        else if(Modifier.isProtected(m)) b.append("protected ");
        if(Modifier.isStatic(m)) b.append("static ");
        if(Modifier.isAbstract(m)&&!iface) b.append("abstract ");
        return b.toString();
    }
    static String params(Type[] ts,Class<?>[] er,boolean varg,List<String> names){
        StringBuilder b=new StringBuilder();
        for(int i=0;i<ts.length;i++){
            String s=ty(ts[i]);
            if(s==null) return null;
            if(varg&&i==ts.length-1&&s.endsWith("[]")) s=s.substring(0,s.length()-2)+"...";
            b.append(i>0?",":"").append(s).append(" a").append(i);
            names.add("a"+i);
        }
        return b.toString();
    }
    static boolean vis(int m){
        return Modifier.isPublic(m)||Modifier.isProtected(m);
    }
    static void gen(Class<?> c,StringBuilder b,String ind,boolean nested){
        boolean ifc=c.isInterface(),en=c.isEnum(),ann=c.isAnnotation();
        if(ann) return;
        b.append(ind).append(mods(c.getModifiers()&~Modifier.INTERFACE&~Modifier.FINAL&~(ifc?Modifier.ABSTRACT:0),false).replace("static ",nested?"static ":""));
        b.append(ifc?"interface ":en?"enum ":"class ").append(c.getSimpleName()).append(tps(c.getTypeParameters()));
        Type sup=c.getGenericSuperclass();
        if(!ifc&&!en&&sup!=null&&sup!=Object.class){
            String s=ty(sup);
            if(s!=null) b.append(" extends ").append(s);
        }
        List<String> is=new ArrayList<>();
        for(Type t:c.getGenericInterfaces()){
            Class<?> rc=t instanceof Class?(Class<?>)t:(Class<?>)((ParameterizedType)t).getRawType();
            if(!Modifier.isPublic(rc.getModifiers())) continue;
            String s=ty(t);
            if(s!=null&&!s.equals("java.io.Serializable")) is.add(s);
        }
        if(!is.isEmpty()) b.append(ifc?" extends ":" implements ").append(String.join(",",is));
        b.append("{\n");
        String in2=ind+"    ";
        if(en){
            List<String> cs=new ArrayList<>();
            for(Object o:c.getEnumConstants()) cs.add(((Enum<?>)o).name());
            b.append(in2).append(String.join(",",cs)).append(";\n");
        }
        for(Field f:c.getDeclaredFields()){
            if(!vis(f.getModifiers())||f.isSynthetic()||f.isEnumConstant()) continue;
            String s=ty(f.getGenericType());
            if(s==null) continue;
            int m=f.getModifiers();
            String init="";
            boolean cst=false;
            if(Modifier.isStatic(m)&&Modifier.isFinal(m)&&(f.getType().isPrimitive()||f.getType()==String.class)){
                try{
                    f.setAccessible(true);
                    Object v=f.get(null);
                    if(v instanceof String) init="\""+v.toString().replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r","\\r")+"\"";
                    else if(v instanceof Character) init="(char)"+(int)(Character)v;
                    else if(v instanceof Long) init=v+"L";
                    else if(v instanceof Float) init=Float.isNaN((Float)v)||Float.isInfinite((Float)v)?"0f":v+"f";
                    else if(v instanceof Double) init=Double.isNaN((Double)v)||Double.isInfinite((Double)v)?"0d":v+"d";
                    else if(v instanceof Byte) init="(byte)"+v;
                    else if(v instanceof Short) init="(short)"+v;
                    else init=String.valueOf(v);
                    cst=true;
                }catch(Throwable e){}
            }
            if(ifc) b.append(in2).append(s).append(" ").append(f.getName()).append("=").append(cst?init:dflt(f.getType())).append(";\n");
            else b.append(in2).append(mods(m,false).replace("abstract ","")).append(cst?"final ":"").append(s).append(" ").append(f.getName()).append(cst?"="+init:"").append(";\n");
        }
        if(!ifc){
            Constructor<?>[] cs=c.getDeclaredConstructors();
            Constructor<?> sc=null;
            Class<?> sk=c.getSuperclass();
            if(sk!=null&&sk!=Object.class&&!en){
                for(Constructor<?> k:sk.getDeclaredConstructors()){
                    if(!vis(k.getModifiers())) continue;
                    boolean g=true;
                    for(Class<?> p:k.getParameterTypes()) if(!ok(p)) g=false;
                    if(g){
                        sc=k;
                        break;
                    }
                }
            }
            boolean any=false;
            if(!en) for(Constructor<?> k:cs){
                if(!vis(k.getModifiers())||k.isSynthetic()) continue;
                List<String> nm=new ArrayList<>();
                String p=params(k.getGenericParameterTypes().length==k.getParameterTypes().length?k.getGenericParameterTypes():k.getParameterTypes(),k.getParameterTypes(),k.isVarArgs(),nm);
                if(p==null) continue;
                any=true;
                b.append(in2).append(mods(k.getModifiers(),false)).append(tps(k.getTypeParameters())).append(" ").append(c.getSimpleName()).append("(").append(p).append("){\n");
                if(sc!=null){
                    List<String> ds=new ArrayList<>();
                    for(Class<?> q:sc.getParameterTypes()) ds.add(dflt(q));
                    b.append(in2).append("    super(").append(String.join(",",ds)).append(");\n");
                }
                b.append(in2).append("    throw new UnsupportedOperationException();\n").append(in2).append("}\n");
            }
            if(!any&&!en&&!Modifier.isAbstract(c.getModifiers())&&sc==null||(!any&&!en&&sc!=null)){
                b.append(in2).append("protected ").append(c.getSimpleName()).append("(){");
                if(sc!=null){
                    List<String> ds=new ArrayList<>();
                    for(Class<?> q:sc.getParameterTypes()) ds.add(dflt(q));
                    b.append("super(").append(String.join(",",ds)).append(");");
                }
                b.append("}\n");
            }
        }
        for(Method m:c.getDeclaredMethods()){
            if(!vis(m.getModifiers())||m.isSynthetic()||m.isBridge()) continue;
            if(en&&(m.getName().equals("values")||m.getName().equals("valueOf"))) continue;
            String r=ty(m.getGenericReturnType());
            if(r==null) continue;
            List<String> nm=new ArrayList<>();
            Type[] gp=m.getGenericParameterTypes().length==m.getParameterTypes().length?m.getGenericParameterTypes():m.getParameterTypes();
            String p=params(gp,m.getParameterTypes(),m.isVarArgs(),nm);
            if(p==null) continue;
            int md=m.getModifiers();
            boolean abs=Modifier.isAbstract(md);
            b.append(in2);
            if(ifc){
                if(Modifier.isStatic(md)) b.append("static ");
                else if(!abs) b.append("default ");
            }else{
                b.append(mods(md,false));
            }
            String tp=tps(m.getTypeParameters());
            b.append(tp.isEmpty()?"":tp+" ").append(r).append(" ").append(m.getName()).append("(").append(p).append(")");
            if(abs) b.append(";\n");
            else b.append("{ throw new UnsupportedOperationException(); }\n");
        }
        for(Class<?> n:c.getDeclaredClasses()){
            if(!vis(n.getModifiers())) continue;
            gen(n,b,in2,true);
        }
        b.append(ind).append("}\n");
    }
    static void addSup(Class<?> c){
        if(c==null||!shimmed(c.getName())) return;
        if(!wl.add(top(c))) return;
        addSup(c.getSuperclass());
        for(Class<?> i:c.getInterfaces()) addSup(i);
        for(Class<?> n:c.getDeclaredClasses()){
            addSup(n.getSuperclass());
            for(Class<?> i:n.getInterfaces()) addSup(i);
        }
    }
    public static void main(String[] a)throws Exception{
        out=Paths.get(a[1]);
        for(String l:Files.readAllLines(Paths.get(a[0]))){
            l=l.trim();
            if(l.isEmpty()||l.startsWith("#")) continue;
            addSup(Class.forName(l.split("\\$")[0],false,ShimGen.class.getClassLoader()));
        }
        for(int i=0;i<3;i++){
            for(String n:new ArrayList<>(wl)){
                Class<?> c=Class.forName(n,false,null);
                for(Class<?> x:c.getDeclaredClasses()) addSup(x);
            }
        }
        for(String n:wl){
            Class<?> c=Class.forName(n,false,null);
            String mn=mp(n);
            int d=mn.lastIndexOf('.');
            StringBuilder b=new StringBuilder("package "+mn.substring(0,d)+";\n@SuppressWarnings({\"unchecked\",\"rawtypes\"})\n");
            gen(c,b,"",false);
            Path f=out.resolve(mn.replace('.','/')+".java");
            Files.createDirectories(f.getParent());
            Files.write(f,b.toString().getBytes("UTF-8"));
        }
        System.out.println(wl.size()+" shim classes");
    }
}
