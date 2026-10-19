package com.mypenc.swfview

import android.graphics.*
import com.jpexs.decompiler.flash.SWF
import com.jpexs.decompiler.flash.exporters.commonshape.Matrix as SM
import com.jpexs.decompiler.flash.exporters.shape.ShapeExporterBase
import com.jpexs.decompiler.flash.tags.*
import com.jpexs.decompiler.flash.tags.base.*
import com.jpexs.decompiler.flash.timeline.Timeline
import com.jpexs.decompiler.flash.types.*
import java.util.zip.Inflater

class Op(val path:Path,val paint:Paint)

class Rnd(val swf:SWF){
    val bm=HashMap<Int,Bitmap?>()
    val sc=HashMap<Long,List<Op>>()

    fun argb(c:RGB):Int{
        val a=if(c is RGBA) c.alpha else 255
        return (a shl 24) or (c.red shl 16) or (c.green shl 8) or c.blue
    }
    fun mat(m:SM):Matrix{
        val r=Matrix()
        r.setValues(floatArrayOf(m.scaleX.toFloat(),m.rotateSkew1.toFloat(),m.translateX.toFloat(),m.rotateSkew0.toFloat(),m.scaleY.toFloat(),m.translateY.toFloat(),0f,0f,1f))
        return r
    }
    fun bmp(id:Int):Bitmap?{
        if(!bm.containsKey(id)){
            bm[id]=try{ dec(swf.getCharacter(id) as? ImageTag) }catch(e:Throwable){ null }
        }
        return bm[id]
    }
    fun unpre(c:Int,a:Int):Int{
        if(a==0||a==255) return (a shl 24) or (c and 0xffffff)
        val r=Math.min(255,((c shr 16) and 255)*255/a)
        val g=Math.min(255,((c shr 8) and 255)*255/a)
        val b=Math.min(255,(c and 255)*255/a)
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }
    fun dec(t:ImageTag?):Bitmap?{
        if(t==null) return null
        if(t is DefineBitsLossless2Tag) return lossless(t.bitmapFormat,t.bitmapWidth,t.bitmapHeight,t.bitmapColorTableSize,t.zlibBitmapData.rangeData,true)
        if(t is DefineBitsLosslessTag) return lossless(t.bitmapFormat,t.bitmapWidth,t.bitmapHeight,t.bitmapColorTableSize,t.zlibBitmapData.rangeData,false)
        val d=t.originalImageData?.readBytes() ?: return null
        val b=BitmapFactory.decodeByteArray(d,0,d.size) ?: return null
        if(t is DefineBitsJPEG3Tag&&t.hasAlphaChannel()){
            val al=t.imageAlpha
            val w=b.width
            val h=b.height
            if(al.size>=w*h){
                val px=IntArray(w*h)
                b.getPixels(px,0,w,0,0,w,h)
                for(i in px.indices) px[i]=unpre(px[i],al[i].toInt() and 255)
                return Bitmap.createBitmap(px,w,h,Bitmap.Config.ARGB_8888)
            }
        }
        return b
    }
    fun lossless(fmt:Int,w:Int,h:Int,ts:Int,z:ByteArray,al:Boolean):Bitmap?{
        val inf=Inflater()
        inf.setInput(z)
        val bo=java.io.ByteArrayOutputStream()
        val buf=ByteArray(65536)
        while(!inf.finished()){
            val n=inf.inflate(buf)
            if(n==0&&(inf.needsInput()||inf.needsDictionary())) break
            bo.write(buf,0,n)
        }
        inf.end()
        val d=bo.toByteArray()
        val px=IntArray(w*h)
        fun u(i:Int)=d[i].toInt() and 255
        if(fmt==3){
            val cs=if(al) 4 else 3
            val n=ts+1
            val tb=n*cs
            val pal=IntArray(n){ i ->
                if(al) unpre((u(i*4+3) shl 24) or (u(i*4) shl 16) or (u(i*4+1) shl 8) or u(i*4+2),u(i*4+3))
                else (255 shl 24) or (u(i*3) shl 16) or (u(i*3+1) shl 8) or u(i*3+2)
            }
            val rb=(w+3)/4*4
            for(y in 0 until h) for(x in 0 until w){
                val p=tb+y*rb+x
                val ix=if(p<d.size) u(p) else 0
                px[y*w+x]=if(ix<n) pal[ix] else 0
            }
        }else if(fmt==4){
            val rb=(w*2+3)/4*4
            for(y in 0 until h) for(x in 0 until w){
                val p=y*rb+x*2
                if(p+1>=d.size) continue
                val v=(u(p) shl 8) or u(p+1)
                val r=((v shr 10) and 31)*255/31
                val g=((v shr 5) and 31)*255/31
                val b=(v and 31)*255/31
                px[y*w+x]=(255 shl 24) or (r shl 16) or (g shl 8) or b
            }
        }else{
            for(i in 0 until w*h){
                val p=i*4
                if(p+3>=d.size) break
                px[i]=if(al) unpre((u(p) shl 24) or (u(p+1) shl 16) or (u(p+2) shl 8) or u(p+3),u(p))
                else (255 shl 24) or (u(p+1) shl 16) or (u(p+2) shl 8) or u(p+3)
            }
        }
        return Bitmap.createBitmap(px,w,h,Bitmap.Config.ARGB_8888)
    }

    inner class AShape(sn:Int,sh:SHAPE):ShapeExporterBase(0,sn,swf,sh,null){
        val ops=ArrayList<Op>()
        var path=Path()
        var pnt:Paint?=null

        init {
            export()
            flush()
        }
        fun flush(){
            val p=pnt
            if(p!=null&&!path.isEmpty) ops.add(Op(path,p))
            path=Path()
            pnt=null
        }
        fun fp():Paint{
            path.fillType=Path.FillType.EVEN_ODD
            return Paint(Paint.ANTI_ALIAS_FLAG)
        }
        fun grad(t:Int,g:Array<GRADRECORD>,m:SM,sp:Int):Shader?{
            if(g.size<2) return null
            val cl=IntArray(g.size){ argb(g[it].color) }
            val ps=FloatArray(g.size){ g[it].ratio/255f }
            for(i in 1 until ps.size) if(ps[i]<ps[i-1]) ps[i]=ps[i-1]
            val tm=if(sp==1) Shader.TileMode.MIRROR else if(sp==2) Shader.TileMode.REPEAT else Shader.TileMode.CLAMP
            val s=if(t==0x10) LinearGradient(-16384f,0f,16384f,0f,cl,ps,tm) else RadialGradient(0f,0f,16384f,cl,ps,tm)
            s.setLocalMatrix(mat(m))
            return s
        }
        fun bsh(id:Int,m:SM,rep:Boolean):Shader?{
            val b=bmp(id) ?: return null
            val tm=if(rep) Shader.TileMode.REPEAT else Shader.TileMode.CLAMP
            val s=BitmapShader(b,tm,tm)
            s.setLocalMatrix(mat(m))
            return s
        }
        override fun beginShape(){}
        override fun endShape(){}
        override fun beginFills(){}
        override fun endFills(){ flush() }
        override fun beginLines(){ flush() }
        override fun endLines(close:Boolean){ flush() }
        override fun beginFill(c:RGB){
            flush()
            val p=fp()
            p.color=argb(c)
            pnt=p
        }
        override fun beginGradientFill(t:Int,g:Array<GRADRECORD>,m:SM,sp:Int,ip:Int,fr:Float){
            flush()
            val p=fp()
            p.shader=grad(t,g,m,sp)
            pnt=p
        }
        override fun beginBitmapFill(id:Int,m:SM,rep:Boolean,sm:Boolean,ct:ColorTransform?){
            flush()
            val p=fp()
            p.isFilterBitmap=sm
            p.shader=bsh(id,m,rep)
            pnt=p
        }
        override fun endFill(){ flush() }
        fun ls(th:Double,sc:Int,ec:Int,j:Int,ml:Float):Paint{
            flush()
            val p=Paint(Paint.ANTI_ALIAS_FLAG)
            p.style=Paint.Style.STROKE
            p.strokeWidth=Math.max(th.toFloat(),8f)
            p.strokeCap=if(sc==0) Paint.Cap.ROUND else if(sc==2) Paint.Cap.SQUARE else Paint.Cap.BUTT
            p.strokeJoin=if(j==0) Paint.Join.ROUND else if(j==1) Paint.Join.BEVEL else Paint.Join.MITER
            p.strokeMiter=Math.max(ml,1f)
            return p
        }
        override fun lineStyle(th:Double,c:RGB,ph:Boolean,scm:String?,sc:Int,ec:Int,j:Int,ml:Float,nc:Boolean){
            val p=ls(th,sc,ec,j,ml)
            p.color=argb(c)
            pnt=p
        }
        override fun lineGradientStyle(t:Int,g:Array<GRADRECORD>,m:SM,sp:Int,ip:Int,fr:Float){
            val p=ls(20.0,0,0,0,3f)
            p.shader=grad(t,g,m,sp)
            pnt=p
        }
        override fun lineBitmapStyle(id:Int,m:SM,rep:Boolean,sm:Boolean,ct:ColorTransform?){
            val p=ls(20.0,0,0,0,3f)
            p.shader=bsh(id,m,rep)
            pnt=p
        }
        override fun moveTo(x:Double,y:Double){ path.moveTo(x.toFloat(),y.toFloat()) }
        override fun lineTo(x:Double,y:Double){ path.lineTo(x.toFloat(),y.toFloat()) }
        override fun curveTo(cx:Double,cy:Double,ax:Double,ay:Double){ path.quadTo(cx.toFloat(),cy.toFloat(),ax.toFloat(),ay.toFloat()) }
    }

    fun ops(t:Any,ratio:Int):List<Op>{
        val k:Long
        val sh:SHAPE
        val sn:Int
        if(t is ShapeTag){
            k=t.characterId.toLong()
            sh=t.shapes
            sn=t.shapeNum
        }else if(t is MorphShapeTag){
            val q=Math.max(0,Math.min(65535,ratio))/656
            k=t.characterId.toLong()*1000+q+1
            sh=t.getShapeAtRatio(q*656)
            sn=t.shapeNum
        }else return emptyList()
        return sc.getOrPut(k){ try{ AShape(sn,sh).ops }catch(e:Throwable){ emptyList() } }
    }
    fun cf(m:ColorMatrix?):ColorFilter? = if(m==null) null else ColorMatrixColorFilter(m)
    fun ctm(c:ColorTransform?):ColorMatrix?{
        if(c==null) return null
        val m=ColorMatrix()
        m.set(floatArrayOf(
            c.redMulti/256f,0f,0f,0f,c.redAdd.toFloat(),
            0f,c.greenMulti/256f,0f,0f,c.greenAdd.toFloat(),
            0f,0f,c.blueMulti/256f,0f,c.blueAdd.toFloat(),
            0f,0f,0f,c.alphaMulti/256f,c.alphaAdd.toFloat()))
        return m
    }
    fun comb(p:ColorMatrix?,c:ColorMatrix?):ColorMatrix?{
        if(c==null) return p
        if(p==null) return c
        val r=ColorMatrix(c)
        r.postConcat(p)
        return r
    }
    fun drawOps(c:Canvas,os:List<Op>,m:ColorMatrix?){
        val f=cf(m)
        for(o in os){
            o.paint.colorFilter=f
            c.drawPath(o.path,o.paint)
        }
    }
    fun clipOf(ch:CharacterTag?,m:Matrix):Path?{
        if(ch !is ShapeTag) return null
        val p=Path()
        for(o in ops(ch,0)) if(o.paint.style==Paint.Style.FILL) p.addPath(o.path)
        p.transform(m)
        return p
    }
    fun frame(c:Canvas,tl:Timeline,fi:Int,tick:Int,m:ColorMatrix?,dep:Int){
        val n=tl.frameCount
        if(dep>12||n<=0) return
        val f=tl.getFrame(((fi%n)+n)%n) ?: return
        val st=ArrayList<IntArray>() //clip end depth,saved flag
        for((d,ds) in f.layers){
            while(st.isNotEmpty()&&d>st[st.size-1][0]){
                if(st[st.size-1][1]==1) c.restore()
                st.removeAt(st.size-1)
            }
            val ch=ds.character
            val lm=if(ds.matrix!=null) mat(SM(ds.matrix)) else Matrix()
            if(ds.clipDepth>=0){
                val cp=clipOf(ch,lm)
                if(cp!=null){
                    c.save()
                    c.clipPath(cp)
                }
                st.add(intArrayOf(ds.clipDepth,if(cp!=null) 1 else 0))
                continue
            }
            if(!ds.isVisible||ch==null) continue
            val cm=comb(m,ctm(ds.colorTransForm))
            c.save()
            c.concat(lm)
            if(ch is ShapeTag) drawOps(c,ops(ch,0),cm)
            else if(ch is MorphShapeTag) drawOps(c,ops(ch,ds.ratio),cm)
            else if(ch is DefineSpriteTag) frame(c,ch.timeline,tick,tick,cm,dep+1)
            c.restore()
        }
        for(s in st) if(s[1]==1) c.restore()
    }
    fun rect(t:Any):RECT? = when(t){
        is ShapeTag->t.rect
        is MorphShapeTag->t.rect
        is DefineSpriteTag->t.rect
        else->null
    }
    @Synchronized fun render(c:Canvas,it:Item,tick:Int){
        val t=it.tag
        if(it.kind==Item.IMG){
            val b=bmp(it.id) ?: return
            c.drawBitmap(b,0f,0f,null)
        }else{
            c.save()
            c.scale(1/20f,1/20f)
            if(it.kind==Item.FRM) frame(c,swf.timeline,it.fr,tick,null,0)
            else if(t is ShapeTag) drawOps(c,ops(t,0),null)
            else if(t is MorphShapeTag) drawOps(c,ops(t,(tick%30)*65535/29),null)
            else if(t is DefineSpriteTag) frame(c,t.timeline,tick,tick,null,0)
            c.restore()
        }
    }
    //content bounds in px
    fun bounds(it:Item):RectF{
        if(it.kind==Item.IMG){
            val b=bmp(it.id)
            return RectF(0f,0f,(b?.width ?: 1).toFloat(),(b?.height ?: 1).toFloat())
        }
        val r=if(it.kind==Item.FRM) swf.displayRect else rect(it.tag!!)
        if(r==null) return RectF(0f,0f,100f,100f)
        return RectF(r.Xmin/20f,r.Ymin/20f,Math.max(r.Xmax,r.Xmin+20)/20f,Math.max(r.Ymax,r.Ymin+20)/20f)
    }
    //draw item fitted into a square thumb
    @Synchronized fun thumb(it:Item,s:Int):Bitmap{
        val b=Bitmap.createBitmap(s,s,Bitmap.Config.ARGB_8888)
        val c=Canvas(b)
        val r=bounds(it)
        val k=Math.min((s-8f)/Math.max(r.width(),1f),(s-8f)/Math.max(r.height(),1f))
        c.translate((s-r.width()*k)/2,(s-r.height()*k)/2)
        c.scale(k,k)
        c.translate(-r.left,-r.top)
        render(c,it,0)
        return b
    }
}
