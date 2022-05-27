package com.example.huacaolu.adapter


import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.huacaolu.R
import com.example.huacaolu.bean.ExplorePlantBean
import com.example.huacaolu.helper.DataBasePlantHelper
import com.example.huacaolu.utils.Base64Util
import com.example.huacaolu.utils.DataBaseUtil


class StaggeredGridAdapter(
    private val mContext: Context,
    private val resultList: MutableList<ExplorePlantBean.Result>,
) :
    RecyclerView.Adapter<StaggeredGridAdapter.ViewHolder>() {

    private lateinit var listener: OnItemClickListener

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        Log.e("onCreateViewHolder", viewType.toString())
        return ViewHolder(
            LayoutInflater.from(mContext)
                .inflate(R.layout.fragment_explore_recycler_view_item, parent, false)
        )
    }

    @SuppressLint("Range")
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onBindViewHolder(holder: ViewHolder, @SuppressLint("RecyclerView") position: Int) {
        Glide.with(mContext).load(resultList[position].imagePath).into(holder.mPlantImage)
        holder.mPlantName.text = resultList[position].name
        holder.mPlantImage.setOnClickListener(View.OnClickListener {
            listener.clickImage(resultList[position].url)
        })
        // TODO 数据库查询操作
        var isLike = false
        var isColl = false
        val urlBase64String = Base64Util.encodeString(resultList[position].url)
        val cursor = DataBaseUtil.getInstance().queryByID(urlBase64String)
        if (cursor.moveToFirst()){
            do {
                val fabulous = cursor.getInt(cursor.getColumnIndex(DataBasePlantHelper.FABULOUS))
                isLike = if (fabulous == 0) {
                    holder.mPlantLike.setImageResource(R.drawable.icon_fabulous_unlike)
                    false
                }else{
                    holder.mPlantLike.setImageResource(R.drawable.icon_fabulous_like)
                    true
                }
                val collection = cursor.getInt(cursor.getColumnIndex(DataBasePlantHelper.COLLECTION))
                isColl = if (collection == 0) {
                    holder.mPlantColl.setImageResource(R.drawable.icon_collection_unlike)
                    false
                }else{
                    holder.mPlantColl.setImageResource(R.drawable.icon_collection_islike)
                    true
                }
            }while (cursor.moveToNext())
        }else{
            isLike = false
            isColl = false
        }

        cursor.close()

        holder.mPlantLike.setOnClickListener(View.OnClickListener {
            isLike = if (isLike) {
                holder.mPlantLike.setImageResource(R.drawable.icon_fabulous_unlike)
                false
            }else {
                holder.mPlantLike.setImageResource(R.drawable.icon_fabulous_like)
                true
            }
            listener.clickLike(resultList[position])

        })
        holder.mPlantColl.setOnClickListener(View.OnClickListener {
            isColl = if (isColl) {
                holder.mPlantColl.setImageResource(R.drawable.icon_collection_unlike)
                false
            }else {
                holder.mPlantColl.setImageResource(R.drawable.icon_collection_islike)
                true
            }
            listener.clickColl(resultList[position])
            // 使用下面的方法去刷新整个条目的数据，但是会造成条目中耗时的操作会闪一下，例如网络加载图片
            // 此时需要一个三级缓存

        })
    }

    override fun getItemCount(): Int {
        return resultList.size
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val mPlantImage: ImageView = itemView.findViewById<View>(R.id.iv_plant_image) as ImageView
        val mPlantName: TextView = itemView.findViewById<View>(R.id.tv_plant_name) as TextView
        val mPlantLike: ImageView = itemView.findViewById<View>(R.id.iBtn_fabulous) as ImageView
        val mPlantColl: ImageView = itemView.findViewById<View>(R.id.iBtn_collection) as ImageView
    }

    interface OnItemClickListener {
        fun clickImage(url : String)
        fun clickLike(explorePlantBean : ExplorePlantBean.Result)
        fun clickColl(explorePlantBean : ExplorePlantBean.Result)
    }

    fun setOnItemClickListener (listener : OnItemClickListener){
        this.listener = listener
    }
}
