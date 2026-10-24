package az.healthchecker.data
import android.content.Context
import az.healthchecker.domain.InboxPhoto
import az.healthchecker.domain.PhotoKind
import az.healthchecker.domain.tr
import java.io.File

/** Photos for later in the app's private files; they never appear in the phone's gallery and are not in backups. */
class PhotoInbox(context:Context) {
    private val dir=File(context.applicationContext.filesDir,"photo-inbox")
    fun list():List<InboxPhoto> = dir.listFiles()?.mapNotNull{InboxPhoto.parse(it.name)}.orEmpty().sortedByDescending{it.takenAt}
    fun file(photo:InboxPhoto):File = File(dir,photo.fileName)
    /** Stores a photo taken at [takenAt]; a second photo in the same millisecond moves on by one. */
    fun add(kind:PhotoKind,takenAt:Long,jpeg:ByteArray):InboxPhoto {
        dir.mkdirs()
        var photo=InboxPhoto(kind,takenAt)
        while(file(photo).exists()||file(photo.copy(kind=PhotoKind.entries.first{it!=kind})).exists())photo=photo.copy(takenAt=photo.takenAt+1)
        write(photo,jpeg);return photo
    }
    /** Written to a temporary file first, so a failed write never leaves half a photo. */
    fun write(photo:InboxPhoto,jpeg:ByteArray) {
        dir.mkdirs();val target=file(photo);val tmp=File(dir,photo.fileName+".tmp")
        tmp.writeBytes(jpeg)
        if(!tmp.renameTo(target)){target.delete();check(tmp.renameTo(target)){tr("Не удалось сохранить фото","Could not save the photo")}}
    }
    fun read(photo:InboxPhoto):ByteArray? = file(photo).takeIf{it.isFile}?.readBytes()
    fun delete(photo:InboxPhoto) { file(photo).delete() }
    fun setKind(photo:InboxPhoto,kind:PhotoKind):InboxPhoto {
        if(photo.kind==kind)return photo
        val moved=photo.copy(kind=kind)
        check(file(photo).renameTo(file(moved))){tr("Не удалось изменить фото","Could not change the photo")}
        return moved
    }
}
