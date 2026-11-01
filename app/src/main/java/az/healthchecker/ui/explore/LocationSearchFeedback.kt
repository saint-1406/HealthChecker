package az.healthchecker.ui.explore

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import az.healthchecker.domain.tr

@Composable fun LocationSearchFeedback(vm:ExploreViewModel) {
    val progress by vm.locating.collectAsState()
    progress?.let { p ->
        Column(Modifier.fillMaxWidth().padding(horizontal=12.dp).testTag("locationSearch")) {
            Text(tr("Определяем место · ${p.elapsedSeconds} с","Finding location · ${p.elapsedSeconds} s"),style=MaterialTheme.typography.bodyMedium)
            Text(tr("Первый поиск GPS может занять до 2 минут. На открытом месте сигнал лучше.",
                "The first GPS fix can take up to 2 minutes. Reception is better outdoors."),style=MaterialTheme.typography.bodySmall)
            val sources=p.providers.joinToString { when(it){"gps"->"GPS";"network"->tr("сеть","network");"fused"->tr("комбинированный","combined");else->it} }
            Text(tr("Источники: ","Sources: ")+sources,style=MaterialTheme.typography.bodySmall)
            p.visibleSatellites?.let { visible -> Text(tr("Спутников: $visible · используется: ${p.usedSatellites ?: 0}",
                "Satellites: $visible · used: ${p.usedSatellites ?: 0}"),style=MaterialTheme.typography.bodySmall) }
            TextButton(onClick={vm.cancelLocate()},modifier=Modifier.heightIn(min=48.dp).testTag("locationCancel")){Text(tr("Отменить поиск","Cancel search"))}
        }
    }
}
