package jawt;

import jawt.geom.AffineTransform;
import jawt.geom.Rectangle2D;
import jawt.image.ColorModel;

public class Color implements Paint,java.io.Serializable {
    public static final Color white=new Color(255,255,255);
    public static final Color WHITE=white;
    public static final Color lightGray=new Color(192,192,192);
    public static final Color LIGHT_GRAY=lightGray;
    public static final Color gray=new Color(128,128,128);
    public static final Color GRAY=gray;
    public static final Color darkGray=new Color(64,64,64);
    public static final Color DARK_GRAY=darkGray;
    public static final Color black=new Color(0,0,0);
    public static final Color BLACK=black;
    public static final Color red=new Color(255,0,0);
    public static final Color RED=red;
    public static final Color pink=new Color(255,175,175);
    public static final Color PINK=pink;
    public static final Color orange=new Color(255,200,0);
    public static final Color ORANGE=orange;
    public static final Color yellow=new Color(255,255,0);
    public static final Color YELLOW=yellow;
    public static final Color green=new Color(0,255,0);
    public static final Color GREEN=green;
    public static final Color magenta=new Color(255,0,255);
    public static final Color MAGENTA=magenta;
    public static final Color cyan=new Color(0,255,255);
    public static final Color CYAN=cyan;
    public static final Color blue=new Color(0,0,255);
    public static final Color BLUE=blue;
    int val;

    public Color(int r,int g,int b){
        this(r,g,b,255);
    }
    public Color(int r,int g,int b,int a){
        val=((a&255)<<24)|((r&255)<<16)|((g&255)<<8)|(b&255);
    }
    public Color(int rgb){
        val=0xff000000|rgb;
    }
    public Color(int rgba,boolean hasAlpha){
        val=hasAlpha?rgba:(0xff000000|rgba);
    }
    public Color(float r,float g,float b){
        this((int)(r*255+0.5f),(int)(g*255+0.5f),(int)(b*255+0.5f));
    }
    public Color(float r,float g,float b,float a){
        this((int)(r*255+0.5f),(int)(g*255+0.5f),(int)(b*255+0.5f),(int)(a*255+0.5f));
    }
    public int getRed(){
        return (val>>16)&255;
    }
    public int getGreen(){
        return (val>>8)&255;
    }
    public int getBlue(){
        return val&255;
    }
    public int getAlpha(){
        return (val>>>24)&255;
    }
    public int getRGB(){
        return val;
    }
    public int getTransparency(){
        int a=getAlpha();
        return a==255?Transparency.OPAQUE:a==0?Transparency.BITMASK:Transparency.TRANSLUCENT;
    }
    public Color brighter(){
        int r=getRed(),g=getGreen(),b=getBlue();
        int i=(int)(1.0/(1.0-0.7));
        if(r==0&&g==0&&b==0) return new Color(i,i,i,getAlpha());
        if(r>0&&r<i) r=i;
        if(g>0&&g<i) g=i;
        if(b>0&&b<i) b=i;
        return new Color(Math.min((int)(r/0.7),255),Math.min((int)(g/0.7),255),Math.min((int)(b/0.7),255),getAlpha());
    }
    public Color darker(){
        return new Color(Math.max((int)(getRed()*0.7),0),Math.max((int)(getGreen()*0.7),0),Math.max((int)(getBlue()*0.7),0),getAlpha());
    }
    public float[] getRGBComponents(float[] a){
        float[] f=a==null?new float[4]:a;
        f[0]=getRed()/255f;
        f[1]=getGreen()/255f;
        f[2]=getBlue()/255f;
        f[3]=getAlpha()/255f;
        return f;
    }
    public float[] getRGBColorComponents(float[] a){
        float[] f=a==null?new float[3]:a;
        f[0]=getRed()/255f;
        f[1]=getGreen()/255f;
        f[2]=getBlue()/255f;
        return f;
    }
    public static Color decode(String s){
        return new Color(Integer.decode(s).intValue());
    }
    public static int HSBtoRGB(float h,float s,float v){
        int r=0,g=0,b=0;
        if(s==0){
            r=g=b=(int)(v*255f+0.5f);
        }else{
            float hh=(h-(float)Math.floor(h))*6f;
            float f=hh-(float)Math.floor(hh);
            float p=v*(1f-s),q=v*(1f-s*f),t=v*(1f-(s*(1f-f)));
            switch((int)hh){
                case 0: r=(int)(v*255f+.5f);g=(int)(t*255f+.5f);b=(int)(p*255f+.5f);break;
                case 1: r=(int)(q*255f+.5f);g=(int)(v*255f+.5f);b=(int)(p*255f+.5f);break;
                case 2: r=(int)(p*255f+.5f);g=(int)(v*255f+.5f);b=(int)(t*255f+.5f);break;
                case 3: r=(int)(p*255f+.5f);g=(int)(q*255f+.5f);b=(int)(v*255f+.5f);break;
                case 4: r=(int)(t*255f+.5f);g=(int)(p*255f+.5f);b=(int)(v*255f+.5f);break;
                default: r=(int)(v*255f+.5f);g=(int)(p*255f+.5f);b=(int)(q*255f+.5f);
            }
        }
        return 0xff000000|(r<<16)|(g<<8)|b;
    }
    public static float[] RGBtoHSB(int r,int g,int b,float[] o){
        float[] h=o==null?new float[3]:o;
        int mx=Math.max(r,Math.max(g,b)),mn=Math.min(r,Math.min(g,b));
        float v=mx/255f,s=mx!=0?(float)(mx-mn)/mx:0,hue;
        if(s==0) hue=0;
        else{
            float rc=(float)(mx-r)/(mx-mn),gc=(float)(mx-g)/(mx-mn),bc=(float)(mx-b)/(mx-mn);
            hue=r==mx?bc-gc:g==mx?2f+rc-bc:4f+gc-rc;
            hue/=6f;
            if(hue<0) hue+=1f;
        }
        h[0]=hue;
        h[1]=s;
        h[2]=v;
        return h;
    }
    public static Color getHSBColor(float h,float s,float b){
        return new Color(HSBtoRGB(h,s,b));
    }
    public int hashCode(){
        return val;
    }
    public boolean equals(Object o){
        return o instanceof Color&&((Color)o).val==val;
    }
    public String toString(){
        return getClass().getName()+"[r="+getRed()+",g="+getGreen()+",b="+getBlue()+"]";
    }
    public PaintContext createContext(ColorModel cm,Rectangle r,Rectangle2D r2,AffineTransform at,RenderingHints h){
        throw new UnsupportedOperationException();
    }
}
