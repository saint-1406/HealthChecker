package az.healthchecker.domain

import java.util.Base64

/** Small private JPEG, embedded in the card so database and backup restore it together. */
const val PRODUCT_PHOTO_MAX_BYTES=180*1024
const val PRODUCT_PHOTO_MAX_PX=960
fun validateProductPhoto(data:String) {
    require(data.length<=((PRODUCT_PHOTO_MAX_BYTES+2)/3)*4){tr("Фото товара слишком большое","The product photo is too large")}
    val bytes=Base64.getDecoder().decode(data)
    require(bytes.size in 4..PRODUCT_PHOTO_MAX_BYTES && bytes[0]==0xff.toByte() && bytes[1]==0xd8.toByte() &&
        bytes[bytes.lastIndex-1]==0xff.toByte() && bytes.last()==0xd9.toByte()) {
        tr("Фото товара повреждено","The product photo is damaged")
    }
}
