package com.example.huacaolu.fragment

import android.os.*
import android.annotation.SuppressLint
import android.app.Activity.RESULT_OK
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.provider.MediaStore
import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import com.baidu.aip.imageclassify.AipImageClassify
import com.bumptech.glide.Glide
import com.example.huacaolu.R
import com.example.huacaolu.api.ParsePlant
import com.example.huacaolu.ui.MyPopupWindow
import java.io.FileOutputStream
import java.io.IOException


private const val ARG_PARAM1 = "param1"
private const val ARG_PARAM2 = "param2"

class SearchFragment : Fragment() {

    private val TAG = "SearchFragment"
    private var param1: String? = null
    private var param2: String? = null

    private val isNeedCropImage = true

    // 拍照识别
    val TAKE_PHOTO = 1
    val CHOOSE_PHOTO = 2
    val CROP_IMAGE = 3
    val HANDLERCROPIMAGE = 4

    //在注册表中配置的provider
    val FILE_PROVIDER_AUTHORITY = "com.example.huacaolu.fileProvider"

    lateinit var mPopupWindow: MyPopupWindow
    lateinit var mIvShowImage: ImageView
    lateinit var mIvSearch: ImageView
    lateinit var mIvTakePhoto: ImageView
    lateinit var mEtSearch: EditText

    lateinit var client: AipImageClassify

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            param1 = it.getString(ARG_PARAM1)
            param2 = it.getString(ARG_PARAM2)
        }
    }

    private val mHandler:Handler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            super.handleMessage(msg)
            when(msg.what){
                HANDLERCROPIMAGE -> {
                    Log.e(TAG,"Handler what =  ${msg.what} obj = ${msg.obj.toString()}")
                    val filePath = msg.obj.toString()
                    Handler(Looper.getMainLooper()).post{
                        showImage(filePath)
                    }
                    ParsePlant.plant(msg.obj.toString(),parsePlantCallback)
                }
            }
        }
    }

    private fun showImage(filePath: String) {
        Glide.with(requireContext()).load(filePath).centerCrop().into(mIvShowImage)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_search, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initAPI()
        initView(view)
        initPopWindow()
    }

    private fun initAPI() {
        client = AipImageClassify(APP_ID, API_KEY, SECRET_KEY)
        client.setConnectionTimeoutInMillis(2000)
        client.setSocketTimeoutInMillis(60000)
    }

    private fun initView(view: View) {
        mIvSearch = view.findViewById<ImageView>(R.id.search_button)
        mIvTakePhoto = view.findViewById<ImageView>(R.id.search_take_photo)
        mEtSearch = view.findViewById<EditText>(R.id.search_edit_text)
        mIvShowImage = view.findViewById<ImageView>(R.id.iv_show_image)
        mIvShowImage.scaleType = ImageView.ScaleType.CENTER_CROP
        mIvSearch.setOnClickListener {
            val searchText = mEtSearch.text.toString()
            if (!TextUtils.isEmpty(searchText)) {
                searchPlant(searchText)
            } else {
                Toast.makeText(requireContext(), "请输入需要查询的花草名", Toast.LENGTH_SHORT).show()
            }
        }

        mIvTakePhoto.setOnClickListener {
            mPopupWindow.showPopupWindow(view)
        }
    }

    private fun initPopWindow() {
        mPopupWindow = MyPopupWindow(requireContext())
        mPopupWindow.setPopupWindowCallBack(object : MyPopupWindow.CallBack {
            override fun clickTakePhoto() {
                takePhoto()
            }

            override fun clickChooseImage() {
                chooseImage()
            }

            override fun clickCancel() {
                Toast.makeText(requireContext(), "未选择", Toast.LENGTH_SHORT).show()
            }
        })
        val width = activity?.windowManager?.defaultDisplay?.width
        mPopupWindow.width = width!! - 200
    }


    private fun chooseImage() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        intent.type = "image/*"
        startActivityForResult(intent, CHOOSE_PHOTO)
    }


    private fun takePhoto() {
        Toast.makeText(requireContext(), "打开相机", Toast.LENGTH_SHORT).show()
        val outputImage = requireContext().getExternalFilesDir("IMG_" + System.currentTimeMillis() + ".jpg")!!
        if (outputImage.exists()) {
            outputImage.delete()
        }
        outputImage.createNewFile()
        val mImageUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            FileProvider.getUriForFile(requireContext(), FILE_PROVIDER_AUTHORITY, outputImage)
        } else {
            Uri.fromFile(outputImage)
        }
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE) //打开相机的Intent
        intent.putExtra(MediaStore.EXTRA_OUTPUT, mImageUri)
        startActivityForResult(intent, TAKE_PHOTO) //打开相机
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode === RESULT_OK) {
            when (requestCode) {
                TAKE_PHOTO,CHOOSE_PHOTO,CROP_IMAGE -> {
                    getImage(data)
                }
                else -> {

                }
            }
        }
    }

    @SuppressLint("Range")
    private fun getImage(data: Intent?) {
        val uri = data?.data!!
        uri.apply {
            val filePathColumn = arrayOf(MediaStore.Images.Media.DATA)
            val cursor = requireContext().contentResolver?.query(uri, filePathColumn, null, null, null)
            cursor?.moveToFirst()
            val filePath = cursor?.getString(cursor.getColumnIndex(MediaStore.Images.Media.DATA))
            cursor?.close()
            filePath?.let{pictureCropping(filePath)}
        }
    }

    // 对图片进行裁剪
    private fun pictureCropping(filePath : String?) {
        val result = filePath?.let { getPictureSize(it) }!!
        if (result == -1) {
            Toast.makeText(requireContext(), "图片选择不符合标准，请重新选择",Toast.LENGTH_SHORT).show()
            return
        }
        // 裁剪是耗时操作，需要在子线程中进行，在裁剪完之后需要使用handler去通知其他需要使用裁剪结果的对象，告知结果可使用了。
        // 一般这种在异步操作，在本类中使用handler，如果是外部需要结果，则使用接口回调，参考ParsePlant的结果回调
        object : Thread() {
            override fun run() {
                super.run()
                val bitmap = Glide.with(requireContext()).asBitmap().load(filePath).submit(result,result).get()
                val file = requireContext().getExternalFilesDir("IMG_" + System.currentTimeMillis() + ".jpg")!!
                if (file.exists()) {
                    file.delete()
                }
                val outputStream = FileOutputStream(file)
                try {
                    bitmap.compress(Bitmap.CompressFormat.JPEG,10,outputStream)
                    outputStream.flush()
                    outputStream.close()
                    val message = mHandler.obtainMessage()
                    message.what = HANDLERCROPIMAGE
                    message.obj = file.path.toString()
                    mHandler.handleMessage(message)
                }catch (e : IOException){
                    Toast.makeText(requireContext(),"裁剪图片异常，请重试",Toast.LENGTH_SHORT).show()
                }

            }
        }.start()
    }

    // 获取图片需要裁剪的大小
    // 此处主要是API的要求，图片不能长和宽皆不能小于15px，同时不能大于4096px
    private fun getPictureSize(path: String): Int {
        val bitmap = BitmapFactory.decodeFile(path)
        val height = bitmap.height
        val width = bitmap.width
        Log.e(TAG, "通过bitmap获取到的图片大小width: $width height: $height")
        return if (width > 4096 && height > 4096) {
            4096
        }else if ((height < 15 || width < 15)) {
            -1
        }else {
            if (width > height) {
                height
            }else {
                width
            }
        }
    }

    // 系统裁剪，有bug：在图片过大的时候会卡住主线程，同样也会造成OOM
    private fun pictureCropping(uri: Uri) {
        val intent = Intent("com.android.camera.action.CROP");
        intent.setDataAndType(uri, "image/*");
        intent.putExtra("crop", "true");
        // aspectX aspectY 是宽高的比例
        intent.putExtra("aspectX", 1)
        intent.putExtra("aspectY", 1)
        // outputX outputY 是裁剪图片宽高
        intent.putExtra("outputX", 4096)
        intent.putExtra("outputY", 4096)
        intent.putExtra("return-data", true);
        startActivityForResult(intent, CROP_IMAGE);
    }

    private val parsePlantCallback: ParsePlant.ParsePlantCallback = object : ParsePlant.ParsePlantCallback {
        override fun parsePlantSuccess(string: String) {
            Log.e(TAG, "onActivityResult: parsePlantSuccess = $string")
        }

        override fun parsePlantFailure(string: String) {
            Log.e(TAG, "onActivityResult: parsePlantFailure = $string")
        }
    }


    private fun rotateBitmap(bitmap: Bitmap, degree: Int): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(degree.toFloat())
        val rotatedBitmap =
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        bitmap.recycle() // 将不再使用的Bitmap回收
        return rotatedBitmap
    }

    // 文字搜索植物
    private fun searchPlant(plant: String) {
        // TODO 获取string 搜索内容展示结果
        Log.e(TAG, plant)
    }

    companion object {
        const val APP_ID = "25964377"
        const val API_KEY = "U65Bwu0iGBsfOjV7uWTArjvf"
        const val SECRET_KEY = "U65Bwu0iGBsfOjV7uWTArjvf"

        @JvmStatic
        fun newInstance(param1: String) =
            SearchFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PARAM1, param1)
                    putString(ARG_PARAM2, param2)
                }
            }
    }

}
