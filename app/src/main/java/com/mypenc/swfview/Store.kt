package com.mypenc.swfview

import android.content.Context
import android.net.Uri
import com.jpexs.decompiler.flash.SWF
import com.jpexs.decompiler.flash.tags.*
import com.jpexs.decompiler.flash.tags.base.*

class Item(val kind:Int,val id:Int,val name:String,val tag:Any?,val fr:Int=0){
    companion object{
        const val IMG=0
        const val SHP=1
        const val MRP=2
        const val SPR=3
        const val FRM=4
        const val BTN=5
        const val TXT=6
        const val FNT=7
        const val SND=8
        val names=arrayOf("Image","Shape","Morph","Sprite","Frame","Button","Text","Font","Sound")
    }
    fun frames():Int = when{
        kind==SPR -> (tag as DefineSpriteTag).timeline.frameCount
        kind==MRP -> 30
        else -> 1
    }
    val preview get()=kind<=FRM
}

object Store{
    var swf:SWF?=null
    var rnd:Rnd?=null
    var cur:Item?=null
    val cats=LinkedHashMap<String,List<Item>>()

    fun load(ctx:Context,uri:Uri){
        val s=ctx.contentResolver.openInputStream(uri)!!.use{ SWF(it,false) }
        val im=ArrayList<Item>()
        val sh=ArrayList<Item>()
        val mo=ArrayList<Item>()
        val sp=ArrayList<Item>()
        val bt=ArrayList<Item>()
        val tx=ArrayList<Item>()
        val fo=ArrayList<Item>()
        val so=ArrayList<Item>()
        for((id,ch) in s.getCharacters(false).toSortedMap()){
            val nm=ch.exportName ?: ch.classNames.firstOrNull() ?: ""
            val lb=if(nm.isEmpty()) "$id" else "$id $nm"
            when(ch){
                is ImageTag -> im.add(Item(Item.IMG,id,lb,ch))
                is ShapeTag -> sh.add(Item(Item.SHP,id,lb,ch))
                is MorphShapeTag -> mo.add(Item(Item.MRP,id,lb,ch))
                is DefineSpriteTag -> sp.add(Item(Item.SPR,id,lb,ch))
                is ButtonTag -> bt.add(Item(Item.BTN,id,lb,ch))
                is TextTag -> tx.add(Item(Item.TXT,id,lb,ch))
                is FontTag -> fo.add(Item(Item.FNT,id,lb,ch))
                is SoundTag -> so.add(Item(Item.SND,id,lb,ch))
            }
        }
        val fr=ArrayList<Item>()
        for(i in 0 until s.timeline.frameCount) fr.add(Item(Item.FRM,0,"Frame ${i+1}",null,i))
        val m=LinkedHashMap<String,List<Item>>()
        fun put(n:String,l:List<Item>){ if(l.isNotEmpty()) m[n]=l }
        put("Images",im)
        put("Shapes",sh)
        put("Morphs",mo)
        put("Sprites",sp)
        put("Frames",fr)
        put("Buttons",bt)
        put("Texts",tx)
        put("Fonts",fo)
        put("Sounds",so)
        swf=s
        rnd=Rnd(s)
        cats.clear()
        cats.putAll(m)
    }
}
