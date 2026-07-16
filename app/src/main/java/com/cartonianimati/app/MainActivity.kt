package com.cartonianimati.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CartoniAnimatiTheme {
                MainScreen()
            }
        }
    }
}

@Composable
fun CartoniAnimatiTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF6C63FF),
            secondary = Color(0xFF03DAC5),
            background = Color(0xFF0F0F23),
            surface = Color(0xFF1A1A2E),
            onPrimary = Color.White,
            onSecondary = Color.Black,
            onBackground = Color.White,
            onSurface = Color.White
        ),
        content = content
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    var currentScreen by remember { mutableStateOf("home") }
    var selectedContent by remember { mutableStateOf<ContentItem?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Cartoni Animati", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1A1A2E)),
                actions = {
                    IconButton(onClick = { selectedContent = null; currentScreen = "search" }) {
                        Icon(Icons.Default.Search, "Cerca")
                    }
                    IconButton(onClick = { selectedContent = null; currentScreen = "home" }) {
                        Icon(Icons.Default.Home, "Home")
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF1A1A2E)) {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Home, "Home") },
                    label = { Text("Home") },
                    selected = currentScreen == "home" && selectedContent == null,
                    onClick = { selectedContent = null; currentScreen = "home" }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Animation, "Anime") },
                    label = { Text("Anime") },
                    selected = currentScreen == "anime-ita" && selectedContent == null,
                    onClick = { selectedContent = null; currentScreen = "anime-ita" }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Subtitles, "Sub-Ita") },
                    label = { Text("Sub-Ita") },
                    selected = currentScreen == "contatti" && selectedContent == null,
                    onClick = { selectedContent = null; currentScreen = "contatti" }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Movie, "Film") },
                    label = { Text("Film") },
                    selected = currentScreen == "film-animazione" && selectedContent == null,
                    onClick = { selectedContent = null; currentScreen = "film-animazione" }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Tv, "Serie TV") },
                    label = { Text("Serie TV") },
                    selected = currentScreen == "serie-tv" && selectedContent == null,
                    onClick = { selectedContent = null; currentScreen = "serie-tv" }
                )
            }
        },
        containerColor = Color(0xFF0F0F23)
    ) { padding ->
        when {
            selectedContent != null -> {
                DetailScreen(
                    content = selectedContent!!,
                    onBack = { selectedContent = null },
                    onPlay = { url, title ->
                        val intent = Intent(context, PlayerActivity::class.java).apply {
                            putExtra("video_url", url)
                            putExtra("title", title)
                        }
                        context.startActivity(intent)
                    },
                    modifier = Modifier.padding(padding)
                )
            }
            currentScreen == "search" -> {
                SearchScreen(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    onItemSelected = { selectedContent = it },
                    modifier = Modifier.padding(padding)
                )
            }
            currentScreen == "home" -> {
                HomeScreen(onItemSelected = { selectedContent = it }, modifier = Modifier.padding(padding))
            }
            else -> {
                CategoryScreen(
                    categorySlug = currentScreen,
                    onItemSelected = { selectedContent = it },
                    modifier = Modifier.padding(padding)
                )
            }
        }
    }
}

@Composable
fun HomeScreen(onItemSelected: (ContentItem) -> Unit, modifier: Modifier = Modifier) {
    var sections by remember { mutableStateOf<List<CategorySection>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            runCatching { sections = Scraper.scrapeHomepage() }.onFailure { it.printStackTrace() }
        }
        isLoading = false
    }

    if (isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color(0xFF6C63FF)) }
    } else if (sections.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Info, null, tint = Color.Gray, modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(12.dp))
                Text("Nessun contenuto trovato", color = Color.Gray, fontSize = 16.sp)
                Spacer(Modifier.height(4.dp))
                Text("Verifica la connessione e riprova", color = Color(0xFF666666), fontSize = 13.sp)
            }
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            items(sections, key = { it.title }) { section ->
                Text(section.title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(vertical = 4.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    items(section.items, key = { it.url }) { item ->
                        ContentCard(item = item, onClick = { onItemSelected(item) })
                    }
                }
            }
        }
    }
}

@Composable
fun CategoryScreen(categorySlug: String, onItemSelected: (ContentItem) -> Unit, modifier: Modifier = Modifier) {
    var items by remember { mutableStateOf<List<ContentItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(categorySlug) {
        isLoading = true
        items = emptyList()
        withContext(Dispatchers.IO) { runCatching { items = Scraper.scrapeContentList(categorySlug) }.onFailure { it.printStackTrace() } }
        isLoading = false
    }

    if (isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color(0xFF6C63FF)) }
    } else if (items.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Info, null, tint = Color.Gray, modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(12.dp))
                Text("Nessun contenuto in questa categoria", color = Color.Gray, fontSize = 16.sp)
            }
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 150.dp),
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(items, key = { it.url }) { item -> ContentCard(item = item, onClick = { onItemSelected(item) }) }
        }
    }
}

@Composable
fun SearchScreen(query: String, onQueryChange: (String) -> Unit, onItemSelected: (ContentItem) -> Unit, modifier: Modifier = Modifier) {
    var results by remember { mutableStateOf<List<ContentItem>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Re-run the search when (re)entering with a populated query (fixes back-arrow "no results").
    LaunchedEffect(query) {
        if (query.length >= 2) {
            isSearching = true
            withContext(Dispatchers.IO) { runCatching { results = Scraper.search(query) }.onFailure { it.printStackTrace() } }
            isSearching = false
        } else {
            results = emptyList()
        }
    }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = {
                onQueryChange(it)
                if (it.length >= 2) {
                    isSearching = true
                    scope.launch {
                        withContext(Dispatchers.IO) { runCatching { results = Scraper.search(it) }.onFailure { it.printStackTrace() } }
                        isSearching = false
                    }
                } else results = emptyList()
            },
            label = { Text("Cerca anime, film, serie...") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(Modifier.height(16.dp))
        if (isSearching) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color(0xFF6C63FF)) }
        } else if (results.isEmpty() && query.length >= 2) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Clear, null, tint = Color.Gray, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("Nessun risultato per \"$query\"", color = Color.Gray, fontSize = 16.sp)
                }
            }
        } else if (results.isNotEmpty()) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 150.dp),
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(results, key = { it.url }) { item -> ContentCard(item = item, onClick = { onItemSelected(item) }) }
            }
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Search, null, tint = Color(0xFF6C63FF), modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("Cerca anime, film o serie TV", color = Color.Gray, fontSize = 16.sp)
                }
            }
        }
    }
}

@Composable
fun DetailScreen(content: ContentItem, onBack: () -> Unit, onPlay: (String, String) -> Unit, modifier: Modifier = Modifier) {
    var detail by remember { mutableStateOf(content) }
    var isLoading by remember { mutableStateOf(content.episodes.isEmpty()) }
    var expandedEpisode by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(content.url) {
        if (content.episodes.isEmpty()) {
            withContext(Dispatchers.IO) { runCatching { detail = Scraper.scrapeDetail(content.url) }.onFailure { it.printStackTrace() } }
            isLoading = false
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        item {
            Box {
                AsyncImage(
                    model = detail.image.ifEmpty { detail.title },
                    contentDescription = detail.title,
                    modifier = Modifier.fillMaxWidth().height(250.dp).clip(RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)),
                    contentScale = ContentScale.Crop
                )
                Box(
                    Modifier.fillMaxWidth().height(250.dp)
                        .background(androidx.compose.ui.graphics.Brush.verticalGradient(colors = listOf(Color.Transparent, Color(0xFF0F0F23))))
                )
                IconButton(onClick = onBack, modifier = Modifier.padding(8.dp)) {
                    Icon(Icons.Default.ArrowBack, "Indietro", tint = Color.White)
                }
            }
        }
        item {
            Column(Modifier.padding(16.dp)) {
                Text(detail.title, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
                if (detail.originalTitle.isNotBlank()) Text(detail.originalTitle, color = Color.Gray, fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (detail.year.isNotBlank()) InfoChip(detail.year)
                    if (detail.status.isNotBlank()) InfoChip(detail.status)
                    if (detail.country.isNotBlank()) InfoChip(detail.country)
                }
                if (detail.synopsis.isNotBlank()) {
                    Spacer(Modifier.height(16.dp))
                    Text("Trama", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                    Spacer(Modifier.height(8.dp))
                    Text(detail.synopsis, color = Color(0xFFBBBBBB), lineHeight = 22.sp)
                }
            }
        }
        if (isLoading) {
            item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color(0xFF6C63FF)) } }
        }
        if (!isLoading && detail.episodes.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.PlayCircle, null, tint = Color(0xFF666666), modifier = Modifier.size(40.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("Nessun episodio disponibile", color = Color.Gray, fontSize = 14.sp)
                    }
                }
            }
        }
        if (detail.episodes.isNotEmpty()) {
            item {
                Text("Episodi (${detail.episodes.size})", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Color.White, modifier = Modifier.padding(horizontal = 16.dp))
            }
            items(detail.episodes, key = { "${it.url}_${it.number}_${it.title}" }) { episode ->
                EpisodeItem(
                    episode = episode,
                    isExpanded = expandedEpisode == episode.number,
                    onToggleExpand = { expandedEpisode = if (expandedEpisode == episode.number) null else episode.number },
                    onClick = { onPlay(episode.url, episode.title) },
                    onPlayerClick = { playerLink -> onPlay(playerLink.url, "${episode.title} - ${playerLink.label}") }
                )
            }
        }
    }
}

@Composable
fun ContentCard(item: ContentItem, onClick: () -> Unit) {
    Card(
        modifier = Modifier.width(150.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E))
    ) {
        Column {
            if (item.image.isNotBlank()) {
                AsyncImage(
                    model = item.image,
                    contentDescription = item.title,
                    modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxWidth().height(200.dp).background(Color(0xFF2A2A4A)).clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Default.PlayCircle, null, tint = Color(0xFF6C63FF), modifier = Modifier.size(48.dp)) }
            }
            Text(item.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 13.sp, color = Color.White, modifier = Modifier.padding(8.dp))
        }
    }
}

@Composable
fun InfoChip(text: String) {
    Surface(shape = RoundedCornerShape(16.dp), color = Color(0xFF2A2A4A)) {
        Text(text, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp), fontSize = 12.sp, color = Color(0xFF03DAC5))
    }
}

@Composable
fun EpisodeItem(episode: Episode, isExpanded: Boolean, onToggleExpand: () -> Unit, onClick: () -> Unit, onPlayerClick: (PlayerLink) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFF1A1A2E)
    ) {
        Column {
            Surface(
                modifier = Modifier.fillMaxWidth().clickable(onClick = {
                    if (episode.players.size > 1) onToggleExpand() else onClick()
                }),
                color = Color.Transparent
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.PlayCircle, null, tint = Color(0xFF6C63FF), modifier = Modifier.size(36.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(episode.title, color = Color.White, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    if (episode.players.size > 1) {
                        Icon(if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = Color.Gray)
                    } else {
                        Icon(Icons.Default.ChevronRight, null, tint = Color.Gray)
                    }
                }
            }
            if (isExpanded && episode.players.size > 1) {
                Column(modifier = Modifier.padding(start = 48.dp, end = 12.dp, bottom = 12.dp)) {
                    episode.players.forEach { player ->
                        Surface(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp).clickable { onPlayerClick(player) },
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF2A2A4A)
                        ) {
                            Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.PlayArrow, null, tint = Color(0xFF03DAC5), modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(player.label, color = Color.White, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
