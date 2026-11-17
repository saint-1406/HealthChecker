package az.healthchecker.ui.training
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import az.healthchecker.domain.*
import az.healthchecker.ui.*

/**
 * Training → Favorites: starred exercises (tap to log one), starred workout videos (tap to open) and the exercises done
 * most often lately that are not starred yet.
 */
fun LazyListScope.favoriteWorkouts(vm:AppViewModel,snapshot:UserSnapshot,date:String,busy:Boolean,log:(Exercise)->Unit,openVideo:(String)->Unit) {
    val exercises=snapshot.favoriteExercises.mapNotNull{vm.exercise(it)}.sortedBy{it.displayName.lowercase(Lang.locale)}
    val videos=vm.videoList(snapshot).filter{it.id in snapshot.favoriteVideos}.sortedBy{it.title.lowercase(Lang.locale)}
    if(exercises.isEmpty()&&videos.isEmpty())item{Panel(Modifier.testTag("noFavoriteWorkouts")) {
        Icon(Icons.Outlined.StarBorder,null,Modifier.size(32.dp),tint=MaterialTheme.colorScheme.primary)
        SectionTitle(tr("Пока пусто","Nothing here yet"),tr("Отмечайте звездой упражнения (в поиске упражнений и в выполненных) и видеотренировки (в списке видео и на экране видео). Они соберутся здесь.",
            "Star exercises (in exercise search and among the completed ones) and workout videos (in the video list and on a video's screen). They gather here."))
    }}
    if(exercises.isNotEmpty()){
        item{SectionTitle(tr("Упражнения","Exercises"),tr("Нажмите, чтобы записать подход","Tap to log a set"))}
        items(exercises,key={"favex:"+it.id}){e->ExerciseRow(vm,e,true,busy){log(e)}}
    }
    if(videos.isNotEmpty()){
        item{SectionTitle(tr("Видеотренировки","Workout videos"),tr("Нажмите, чтобы открыть видео","Tap to open the video"))}
        items(videos,key={"favvideo:"+it.id}){v->Panel(Modifier.testTag("favoriteVideo")) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Row(Modifier.weight(1f).clickable{openVideo(v.id)}.testTag("favoriteVideoOpen"),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Outlined.SmartDisplay,null,Modifier.size(40.dp),tint=MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f)) {
                        Text(v.title,fontWeight=FontWeight.SemiBold,maxLines=3,overflow=TextOverflow.Ellipsis)
                        Text(listOfNotNull(v.channel.takeIf{it.isNotBlank()},v.durationSec?.let{"${(it+30)/60} ${tr("мин","min")}"}).joinToString(" · "),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                FavoriteStar(true,v.title,!busy){vm.toggleFavoriteVideo(v.id)}
            }
        }}
    }
    val frequent=snapshot.frequentExercises(date).mapNotNull{vm.exercise(it)}
    if(frequent.isNotEmpty()){
        item{SectionTitle(tr("Часто делаете","You often do"),tr("За последние $FREQUENT_DAYS дней. Звезда добавляет в избранное.","Over the last $FREQUENT_DAYS days. The star adds it to favorites."))}
        items(frequent,key={"often:"+it.id}){e->ExerciseRow(vm,e,false,busy){log(e)}}
    }
}

@Composable private fun ExerciseRow(vm:AppViewModel,e:Exercise,starred:Boolean,busy:Boolean,open:()->Unit) = Panel(Modifier.testTag(if(starred)"favoriteExercise" else "frequentExercise")) {
    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        Row(Modifier.weight(1f).clickable(onClick=open).testTag(if(starred)"favoriteExerciseOpen" else "frequentExerciseOpen"),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            ExerciseThumb(vm,e.id)
            Column(Modifier.weight(1f)) {
                Text(e.displayName,fontWeight=FontWeight.SemiBold)
                Text("${categoryLabel(e.category)} · ${e.muscles.joinToString(", "){muscleLabel(it)}}",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        FavoriteStar(starred,e.displayName,!busy){vm.toggleFavoriteExercise(e.id)}
    }
}
