package az.healthchecker.data

import android.content.Context
import android.net.Uri
import az.healthchecker.domain.*
import java.util.Base64

/** Call on an IO worker. Only the normalized JPEG is retained; the picked URI is never persisted. */
fun encodeProductPhoto(context:Context,uri:Uri):String {
    for((size,quality) in listOf(PRODUCT_PHOTO_MAX_PX to 80,720 to 70,480 to 60)) {
        val jpeg=encodeImage(context,uri,size,quality)
        if(jpeg.size<=PRODUCT_PHOTO_MAX_BYTES)return Base64.getEncoder().encodeToString(jpeg).also(::validateProductPhoto)
    }
    throw IllegalArgumentException(tr("Не удалось уменьшить фото. Выберите другую фотографию.","Could not reduce the photo size. Choose another photo."))
}
