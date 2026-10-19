import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import java.util.zip.*;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.commons.*;

public class Remap {
    static final String[][] PK={{"java/awt/","jawt/"},{"javax/imageio/","jimageio/"},{"javax/swing/","jswing/"},{"javax/accessibility/","jacc/"},{"java/beans/","jbeans/"}};
    static final Map<String,byte[]> shim=new HashMap<>();
    static final Map<String,Set<String>> mem=new HashMap<>();
    static final Map<String,String[]> sup=new HashMap<>();
    static final Set<String> miss=new TreeSet<>();
    static final Remapper rm=new Remapper(){
        public String map(String n){
            for(String[] p:PK) if(n.startsWith(p[0])) return p[1]+n.substring(p[0].length());
            return n;
        }
    };

    static boolean isShim(String n){
        for(String[] p:PK) if(n.startsWith(p[1])) return true;
        return false;
    }
    static void idx(String n){
        if(mem.containsKey(n)||!shim.containsKey(n)) return;
        Set<String> s=new HashSet<>();
        mem.put(n,s);
        new ClassReader(shim.get(n)).accept(new ClassVisitor(Opcodes.ASM9){
            public void visit(int v,int a,String nm,String sg,String sp,String[] is){
                sup.put(nm,new String[]{sp==null?"":sp,is==null?"":String.join(",",is)});
            }
            public MethodVisitor visitMethod(int a,String nm,String d,String sg,String[] e){
                s.add(nm+d);
                return null;
            }
            public FieldVisitor visitField(int a,String nm,String d,String sg,Object v){
                s.add(nm+":"+d);
                return null;
            }
        },ClassReader.SKIP_CODE);
    }
    static boolean has(String o,String k){
        if(o==null||o.isEmpty()) return false;
        idx(o);
        if(!shim.containsKey(o)){
            return true; //non shim owner, jdk or android class
        }
        if(mem.get(o).contains(k)) return true;
        String[] s=sup.get(o);
        if(has(s[0],k)) return true;
        for(String i:s[1].split(",")) if(has(i,k)) return true;
        return false;
    }
    static void chk(String from,String o,String k){
        if(o.startsWith("[")||!isShim(o)) return;
        if(!shim.containsKey(o)){
            miss.add(from+" -> missing class "+o);
            return;
        }
        if(k!=null&&!has(o,k)&&!(k.startsWith("<init>")&&false)) miss.add(from+" -> "+o+"."+k);
    }
    static byte[] conv(byte[] b,String nm){
        ClassReader cr=new ClassReader(b);
        ClassWriter cw=new ClassWriter(0);
        ClassVisitor chk=new ClassVisitor(Opcodes.ASM9,cw){
            public MethodVisitor visitMethod(int a,String n,String d,String s,String[] e){
                return new MethodVisitor(Opcodes.ASM9,super.visitMethod(a,n,d,s,e)){
                    public void visitMethodInsn(int op,String o,String n2,String d2,boolean itf){
                        chk(nm,o,n2+d2);
                        super.visitMethodInsn(op,o,n2,d2,itf);
                    }
                    public void visitFieldInsn(int op,String o,String n2,String d2){
                        chk(nm,o,n2+":"+d2);
                        super.visitFieldInsn(op,o,n2,d2);
                    }
                    public void visitTypeInsn(int op,String t){
                        chk(nm,t,null);
                        super.visitTypeInsn(op,t);
                    }
                };
            }
        };
        cr.accept(new ClassRemapper(chk,rm),0);
        return cw.toByteArray();
    }
    //args: out.jar shimDir report.txt in...
    public static void main(String[] a)throws Exception{
        Path sd=Paths.get(a[1]);
        Files.walk(sd).filter(p->p.toString().endsWith(".class")).forEach(p->{
            try{
                String n=sd.relativize(p).toString().replace(File.separatorChar,'/');
                shim.put(n.substring(0,n.length()-6),Files.readAllBytes(p));
            }catch(IOException e){
                throw new UncheckedIOException(e);
            }
        });
        Set<String> seen=new HashSet<>();
        try(JarOutputStream o=new JarOutputStream(new FileOutputStream(a[0]))){
            for(Map.Entry<String,byte[]> e:shim.entrySet()){
                o.putNextEntry(new ZipEntry(e.getKey()+".class"));
                o.write(e.getValue());
                o.closeEntry();
                seen.add(e.getKey()+".class");
            }
            for(int i=3;i<a.length;i++){
                Path in=Paths.get(a[i]);
                if(Files.isDirectory(in)){
                    List<Path> fs=new ArrayList<>();
                    Files.walk(in).filter(Files::isRegularFile).forEach(fs::add);
                    for(Path f:fs) put(o,in.relativize(f).toString().replace(File.separatorChar,'/'),Files.readAllBytes(f),seen);
                }else try(ZipFile z=new ZipFile(in.toFile())){
                    for(Enumeration<? extends ZipEntry> en=z.entries();en.hasMoreElements();){
                        ZipEntry ze=en.nextElement();
                        if(ze.isDirectory()) continue;
                        try(InputStream is=z.getInputStream(ze)){
                            put(o,ze.getName(),is.readAllBytes(),seen);
                        }
                    }
                }
            }
        }
        Files.write(Paths.get(a[2]),miss);
        System.out.println(miss.size()+" unresolved shim refs");
    }
    static void put(JarOutputStream o,String n,byte[] b,Set<String> seen)throws IOException{
        if(n.startsWith("META-INF/")&&(n.endsWith(".SF")||n.endsWith(".RSA")||n.endsWith(".DSA")||n.equals("META-INF/MANIFEST.MF"))) return;
        if(n.equals("module-info.class")||n.startsWith("META-INF/versions/")) return;
        if(!seen.add(n)) return;
        if(n.endsWith(".class")) b=conv(b,n);
        o.putNextEntry(new ZipEntry(n));
        o.write(b);
        o.closeEntry();
    }
}
