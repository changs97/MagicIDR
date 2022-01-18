package com.changs.magicidr.ui.main


import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.View.*
import android.widget.Toast
import androidx.core.content.FileProvider
import com.changs.magicidr.databinding.MainActivityBinding
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import kotlin.math.max
import kotlin.math.sqrt
import org.opencv.core.Mat
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList
import androidx.annotation.NonNull
import androidx.annotation.Nullable

import com.bumptech.glide.request.target.CustomTarget

import com.bumptech.glide.Glide
import com.bumptech.glide.request.transition.Transition


class MainActivity : AppCompatActivity() {


    private val REQUEST_TAKE_PHOTO = 1
    private lateinit var currentPhotoPath: String


    val binding by lazy { MainActivityBinding.inflate(layoutInflater)}

    var btm : Bitmap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)


        // 화면이 만들어 지면서 정장소 권한을 체크 합니다.
        // 권한이 승인되어 있으면 카메라를 호출하는 메소드를 실행합니다.
        binding.button.setOnClickListener {
            cleanView()
            takePictureIntent()
        }

        binding.button2.setOnClickListener {
            if (btm != null) {
                OpenCVLoader.initDebug()
                getImage(btm!!)
            }

        }

    }

    private fun getImage(bitmap : Bitmap) {
        // 흑백영상으로 전환
        val src = Mat()
        val graySrc = Mat()
        Utils.bitmapToMat(bitmap, src)

        Imgproc.cvtColor(src, graySrc, Imgproc.COLOR_BGR2GRAY)

        // 이진화
        val binarySrc = Mat()
        Imgproc.threshold(graySrc, binarySrc, 0.0, 255.0, Imgproc.THRESH_OTSU)

        // 윤곽선 찾기
        val contours = ArrayList<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(
            binarySrc,
            contours,
            hierarchy,
            Imgproc.RETR_EXTERNAL,
            Imgproc.CHAIN_APPROX_NONE
        )

        // 가장 면적이 큰 윤곽선 찾기
        var biggestContour: MatOfPoint? = null
        var biggestContourArea: Double = 0.0
        for (contour in contours) {
            val area = Imgproc.contourArea(contour)
            if (area > biggestContourArea) {
                biggestContour = contour
                biggestContourArea = area
            }
        }


        if (biggestContour == null) {
            binding.textView1.text = "No Contour"
            binding.textView1.visibility = VISIBLE
            Toast.makeText(this, "restart", Toast.LENGTH_SHORT).show()
        }
        // 너무 작아도 안됨
        if (biggestContourArea < 400) {
            binding.textView2.text =  "too small"
            binding.textView2.visibility = VISIBLE
        }

        val candidate2f = MatOfPoint2f(*biggestContour?.toArray())
        val approxCandidate = MatOfPoint2f()
        Imgproc.approxPolyDP(
            candidate2f,
            approxCandidate,
            Imgproc.arcLength(candidate2f, true) * 0.02,
            true
        )

        // 사각형 판별
        if (approxCandidate.rows() != 4) {
            binding.textView3.text = "It's not rectangle"
            binding.textView3.visibility = VISIBLE
        }

        // 컨벡스(볼록한 도형)인지 판별
        if (!Imgproc.isContourConvex(MatOfPoint(*approxCandidate.toArray()))) {
            binding.textView4.text = "It's not convex"
            binding.textView4.visibility = VISIBLE
        }

        if(biggestContour == null || biggestContourArea < 400 || approxCandidate.rows() != 4 ||
            !Imgproc.isContourConvex(MatOfPoint(*approxCandidate.toArray()))) {
            Toast.makeText(this, "restart", Toast.LENGTH_SHORT).show()
        } else {
            // 좌상단부터 시계 반대 방향으로 정점을 정렬한다.
            val points = arrayListOf(
                Point(approxCandidate.get(0, 0)[0], approxCandidate.get(0, 0)[1]),
                Point(approxCandidate.get(1, 0)[0], approxCandidate.get(1, 0)[1]),
                Point(approxCandidate.get(2, 0)[0], approxCandidate.get(2, 0)[1]),
                Point(approxCandidate.get(3, 0)[0], approxCandidate.get(3, 0)[1]),
            )
            points.sortBy { it.x } // x좌표 기준으로 먼저 정렬

            if (points[0].y > points[1].y) {
                val temp = points[0]
                points[0] = points[1]
                points[1] = temp
            }

            if (points[2].y < points[3].y) {
                val temp = points[2]
                points[2] = points[3]
                points[3] = temp
            }
            // 원본 영상 내 정점들
            val srcQuad = MatOfPoint2f().apply { fromList(points) }

            val maxSize = calculateMaxWidthHeight(
                tl = points[0],
                bl = points[1],
                br = points[2],
                tr = points[3]
            )
            val dw = maxSize.width
            val dh = dw * maxSize.height/maxSize.width
            val dstQuad = MatOfPoint2f(
                Point(0.0, 0.0),
                Point(0.0, dh),
                Point(dw, dh),
                Point(dw, 0.0)
            )
            // 투시변환 매트릭스 구하기
            val perspectiveTransform = Imgproc.getPerspectiveTransform(srcQuad, dstQuad)

            // 투시변환 된 결과 영상 얻기
            val dst = Mat()
            Imgproc.warpPerspective(src, dst, perspectiveTransform, Size(dw, dh))

            val bitmap = Bitmap.createBitmap(dst.cols(), dst.rows(), Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(dst, bitmap)
            binding.imageView.setImageBitmap(bitmap)
        }
    }


    fun cleanView() {
        btm = null
        binding.imageView.visibility = INVISIBLE
        binding.textView1.visibility = GONE
        binding.textView2.visibility = GONE
        binding.textView3.visibility = GONE
        binding.textView4.visibility = GONE

    }

    // 사각형 꼭짓점 정보로 사각형 최대 사이즈 구하기
    // 평면상 두 점 사이의 거리는 직각삼각형의 빗변길이 구하기와 동일
    private fun calculateMaxWidthHeight(
        tl:Point,
        tr:Point,
        br:Point,
        bl:Point,
    ): Size {
        // Calculate width
        val widthA = sqrt((tl.x - tr.x) * (tl.x - tr.x) + (tl.y - tr.y) * (tl.y - tr.y))
        val widthB = sqrt((bl.x - br.x) * (bl.x - br.x) + (bl.y - br.y) * (bl.y - br.y))
        val maxWidth = max(widthA, widthB)
        // Calculate height
        val heightA = sqrt((tl.x - bl.x) * (tl.x - bl.x) + (tl.y - bl.y) * (tl.y - bl.y))
        val heightB = sqrt((tr.x - br.x) * (tr.x - br.x) + (tr.y - br.y) * (tr.y - br.y))
        val maxHeight = max(heightA, heightB)
        return Size(maxWidth, maxHeight)
    }



    // 사진 찍는 인텐트
    private fun takePictureIntent(){
        Intent(MediaStore.ACTION_IMAGE_CAPTURE).also { takePictureIntent ->
            takePictureIntent.resolveActivity(packageManager)?.also {
                // 사진 파일을 만듭니다.
                val photoFile: File? = try {
                    createImageFile()
                } catch (ex: IOException) {
                    // Error occurred while creating the File
                    Log.d("test", "error: $ex")
                    null
                }
                // photoUri를 보내는 코드
                photoFile?.also {
                    val photoURI: Uri = FileProvider.getUriForFile(
                        this,
                        "com.changs.magicidr",
                        it
                    )
                    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                    startActivityForResult(takePictureIntent, REQUEST_TAKE_PHOTO)
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_TAKE_PHOTO && resultCode == RESULT_OK) {
            galleryAddPic()
            setPic()
        }
    }

    // 사진 파일을 만드는 메소드
    @Throws(IOException::class)
    private fun createImageFile(): File {
        // Create an image file name
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        val storageDir: File? = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        return File.createTempFile(
            "JPEG_${timeStamp}_", /* prefix */
            ".jpg", /* suffix */
            storageDir /* directory */
        ).apply {
            // Save a file: path for use with ACTION_VIEW intents
            currentPhotoPath = absolutePath
            Log.d("test", "currentPhotoPath : $currentPhotoPath")
        }
    }

    // 갤러리에 파일을 추가하는 함수.
    private fun galleryAddPic() {
        Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE).also { mediaScanIntent ->
            Log.d("test", "currentPhotoPath2 : $currentPhotoPath")
            val f = File(currentPhotoPath)
            mediaScanIntent.data = Uri.fromFile(f)
            sendBroadcast(mediaScanIntent)
        }
    }

    // ImageView에 사진을 넣는 메소드
    private fun setPic() {
        Glide.with(this)
            .asBitmap()
            .load(currentPhotoPath)
            .into(object : CustomTarget<Bitmap?>() {
                override fun onResourceReady(
                    resource: Bitmap,
                    @Nullable transition: Transition<in Bitmap?>?
                ) {
                    // 이미지를 비트맵으로 변환 완료
                    binding.imageView.setImageBitmap(resource)
                    binding.imageView.visibility = VISIBLE
                    btm = resource
                }
                override fun onLoadCleared(@Nullable placeholder: Drawable?) {}
            })

        }


}
