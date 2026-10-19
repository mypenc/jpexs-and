package com.mypenc.swfview

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.LruCache
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import java.util.concurrent.Executors

class MainActivity:Activity(){
    val ex=Executors.newSingleThreadExecutor()
    val lru=LruCache<String,Bitmap>(80)
    var gen=0
    var cat=""
    lateinit var grid:GridView
    lateinit var tabs:LinearLayout
    lateinit var info:TextView

    fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()

    override fun onCreate(b:Bundle?){
        super.onCreate(b)
        val root=LinearLayout(this)
        root.orientation=LinearLayout.VERTICAL
        val top=LinearLayout(this)
        top.gravity=Gravity.CENTER_VERTICAL
        top.setPadding(dp(12),dp(12),dp(12),dp(4))
        val tt=TextView(this)
        tt.text="SWF View"
        tt.textSize=20f
        top.addView(tt,LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f))
        val ob=Button(this)
        ob.text="Open SWF"
        ob.setOnClickListener{ pick() }
        top.addView(ob)
        root.addView(top)
        info=TextView(this)
        info.text="Open a .swf file to browse its bitmaps, shapes, sprites and frames"
        info.setPadding(dp(12),0,dp(12),dp(4))
        root.addView(info)
        val hs=HorizontalScrollView(this)
        tabs=LinearLayout(this)
        hs.addView(tabs)
        root.addView(hs)
        grid=GridView(this)
        grid.numColumns=GridView.AUTO_FIT
        grid.columnWidth=dp(104)
        grid.stretchMode=GridView.STRETCH_COLUMN_WIDTH
        grid.setOnItemClickListener{ _,_,i,_ -> open(Store.cats[cat]!![i]) }
        root.addView(grid,LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1f))
        setContentView(root)
        val u=intent?.data
        if(u!=null) load(u)
        else if(Store.swf!=null) build()
    }
    fun pick(){
        val i=Intent(Intent.ACTION_OPEN_DOCUMENT)
        i.type="*/*"
        i.addCategory(Intent.CATEGORY_OPENABLE)
        startActivityForResult(i,1)
    }
    @Deprecated("old api")
    override fun onActivityResult(rq:Int,rs:Int,d:Intent?){
        super.onActivityResult(rq,rs,d)
        val u=d?.data
        if(rq==1&&rs==RESULT_OK&&u!=null) load(u)
    }
    fun load(u:Uri){
        info.text="Loading..."
        Thread{
            try{
                Store.load(this,u)
                runOnUiThread{ build() }
            }catch(e:Throwable){
                runOnUiThread{ info.text="Failed: ${e.javaClass.simpleName} ${e.message}" }
            }
        }.start()
    }
    fun build(){
        gen++
        lru.evictAll()
        tabs.removeAllViews()
        val s=Store.swf ?: return
        info.text="v${s.version} ${s.frameCount} frames ${s.frameRate}fps ${Store.cats.values.sumOf{ it.size }} items"
        for((n,l) in Store.cats){
            val b=Button(this)
            b.text="$n (${l.size})"
            b.setOnClickListener{ show(n) }
            tabs.addView(b)
        }
        show(Store.cats.keys.firstOrNull() ?: "")
    }
    fun show(n:String){
        cat=n
        val l=Store.cats[n] ?: emptyList()
        grid.adapter=Ad(n,l,gen)
    }
    fun open(i:Item){
        if(!i.preview){
            Toast.makeText(this,"${Item.names[i.kind]} ${i.name}",Toast.LENGTH_SHORT).show()
            return
        }
        Store.cur=i
        startActivity(Intent(this,PreviewActivity::class.java))
    }

    inner class Ad(val cn:String,val l:List<Item>,val g:Int):BaseAdapter(){
        override fun getCount()=l.size
        override fun getItem(p:Int)=l[p]
        override fun getItemId(p:Int)=p.toLong()
        override fun getView(p:Int,v:View?,pr:ViewGroup?):View{
            val c=(v as? LinearLayout) ?: LinearLayout(this@MainActivity).also{
                it.orientation=LinearLayout.VERTICAL
                it.gravity=Gravity.CENTER_HORIZONTAL
                it.setPadding(dp(4),dp(4),dp(4),dp(4))
                val im=ImageView(this@MainActivity)
                im.scaleType=ImageView.ScaleType.FIT_CENTER
                im.setBackgroundColor(0xffeeeeee.toInt())
                it.addView(im,LinearLayout.LayoutParams(dp(96),dp(96)))
                val t=TextView(this@MainActivity)
                t.textSize=11f
                t.maxLines=1
                t.ellipsize=android.text.TextUtils.TruncateAt.END
                t.gravity=Gravity.CENTER
                it.addView(t,LinearLayout.LayoutParams(dp(96),ViewGroup.LayoutParams.WRAP_CONTENT))
            }
            val im=c.getChildAt(0) as ImageView
            val t=c.getChildAt(1) as TextView
            val it=l[p]
            val k="$g:$cn:$p"
            t.text=it.name
            im.tag=k
            im.setImageBitmap(null)
            if(!it.preview){
                im.setBackgroundColor(Color.LTGRAY)
                return c
            }
            im.setBackgroundColor(0xffeeeeee.toInt())
            val h=lru.get(k)
            if(h!=null) im.setImageBitmap(h)
            else ex.execute{
                val bm=try{ Store.rnd?.thumb(it,192) }catch(e:Throwable){ null }
                if(bm!=null){
                    lru.put(k,bm)
                    im.post{ if(im.tag==k) im.setImageBitmap(bm) }
                }
            }
            return c
        }
    }
}
