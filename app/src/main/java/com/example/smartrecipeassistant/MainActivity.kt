package com.example.smartrecipeassistant

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.*
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt

// ============================================================================
// ENTRY POINT & THEME
// ============================================================================

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SmartRecipeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SmartRecipeApp()
                }
            }
        }
    }
}

@Composable
fun SmartRecipeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = Color(0xFFE2A143),
            secondary = Color(0xFF4CAF50),
            background = Color(0xFF121212),
            surface = Color(0xFF1E1E1E),
            onPrimary = Color.White,
            onSurface = Color.White
        )
    } else {
        lightColorScheme(
            primary = Color(0xFFF59E0B),
            secondary = Color(0xFF10B981),
            background = Color(0xFFFFFBEB),
            surface = Color.White,
            onPrimary = Color.White,
            onSurface = Color(0xFF1F2937)
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}

// ============================================================================
// DATA MODELS
// ============================================================================

data class Recipe(
    val id: String,
    val name: String,
    val cuisine: String,
    val category: String,
    val prepTime: Int,
    val cookTime: Int,
    val servings: Int,
    val difficulty: String,
    val imageUrl: String,
    val ingredients: List<Ingredient>,
    val steps: List<String>,
    val isFavorite: Boolean = false,
    val timesCooked: Int = 0
)

data class Ingredient(
    val name: String,
    val quantity: String,
    val isMissingFromPantry: Boolean = false
)

// ============================================================================
// JSON PERSISTENCE HELPERS (org.json — built into Android, no new dependency)
// ============================================================================

fun recipeToJson(recipe: Recipe): JSONObject {
    val obj = JSONObject()
    obj.put("id", recipe.id)
    obj.put("name", recipe.name)
    obj.put("cuisine", recipe.cuisine)
    obj.put("category", recipe.category)
    obj.put("prepTime", recipe.prepTime)
    obj.put("cookTime", recipe.cookTime)
    obj.put("servings", recipe.servings)
    obj.put("difficulty", recipe.difficulty)
    obj.put("imageUrl", recipe.imageUrl)
    obj.put("isFavorite", recipe.isFavorite)
    obj.put("timesCooked", recipe.timesCooked)
    val ingredientsArray = JSONArray()
    recipe.ingredients.forEach { ing ->
        val ingObj = JSONObject()
        ingObj.put("name", ing.name)
        ingObj.put("quantity", ing.quantity)
        ingObj.put("isMissingFromPantry", ing.isMissingFromPantry)
        ingredientsArray.put(ingObj)
    }
    obj.put("ingredients", ingredientsArray)
    val stepsArray = JSONArray()
    recipe.steps.forEach { stepsArray.put(it) }
    obj.put("steps", stepsArray)
    return obj
}

fun jsonToRecipe(obj: JSONObject): Recipe {
    val ingredientsArray = obj.getJSONArray("ingredients")
    val ingredients = (0 until ingredientsArray.length()).map { i ->
        val ingObj = ingredientsArray.getJSONObject(i)
        Ingredient(
            name = ingObj.getString("name"),
            quantity = ingObj.getString("quantity"),
            isMissingFromPantry = ingObj.optBoolean("isMissingFromPantry", false)
        )
    }
    val stepsArray = obj.getJSONArray("steps")
    val steps = (0 until stepsArray.length()).map { stepsArray.getString(it) }
    return Recipe(
        id = obj.getString("id"),
        name = obj.getString("name"),
        cuisine = obj.getString("cuisine"),
        category = obj.getString("category"),
        prepTime = obj.getInt("prepTime"),
        cookTime = obj.getInt("cookTime"),
        servings = obj.getInt("servings"),
        difficulty = obj.getString("difficulty"),
        imageUrl = obj.getString("imageUrl"),
        ingredients = ingredients,
        steps = steps,
        isFavorite = obj.optBoolean("isFavorite", false),
        timesCooked = obj.optInt("timesCooked", 0)
    )
}

fun recipesToJson(recipes: List<Recipe>): String {
    val array = JSONArray()
    recipes.forEach { array.put(recipeToJson(it)) }
    return array.toString()
}

fun jsonToRecipes(json: String): List<Recipe> {
    val array = JSONArray(json)
    return (0 until array.length()).map { jsonToRecipe(array.getJSONObject(it)) }
}

fun stringListToJson(list: List<String>): String {
    val array = JSONArray()
    list.forEach { array.put(it) }
    return array.toString()
}

fun jsonToStringList(json: String): List<String> {
    val array = JSONArray(json)
    return (0 until array.length()).map { array.getString(it) }
}

// ============================================================================
// TIMER SOUND OPTIONS
// ============================================================================

enum class TimerSound(val label: String, val toneType: Int, val durationMs: Int) {
    CLASSIC_BEEP("Classic Beep", ToneGenerator.TONE_PROP_BEEP, 200),
    ALERT_CHIME("Alert Chime", ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 250),
    SOFT_PING("Soft Ping", ToneGenerator.TONE_CDMA_ABBR_ALERT, 150),
    URGENT_ALARM("Urgent Alarm", ToneGenerator.TONE_DTMF_1, 300)
}

// ============================================================================
// VIEW MODEL (Presentation, State Management & Persistence)
// ============================================================================

private const val PREFS_NAME = "smart_recipe_prefs"
private const val KEY_RECIPES = "recipes_json"
private const val KEY_CATEGORIES = "categories_json"
private const val KEY_USER_NAME = "user_name"
private const val KEY_TIMER_SOUND = "timer_sound"

class RecipeViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _recipes = MutableStateFlow(loadRecipes())
    val recipes: StateFlow<List<Recipe>> = _recipes.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _categories = MutableStateFlow(loadCategories())
    val categories: StateFlow<List<String>> = _categories.asStateFlow()

    private val _userName = MutableStateFlow(prefs.getString(KEY_USER_NAME, "") ?: "")
    val userName: StateFlow<String> = _userName.asStateFlow()

    private val _selectedTimerSound = MutableStateFlow(
        TimerSound.values().find { it.name == prefs.getString(KEY_TIMER_SOUND, null) } ?: TimerSound.CLASSIC_BEEP
    )
    val selectedTimerSound: StateFlow<TimerSound> = _selectedTimerSound.asStateFlow()

    private val _pantry = MutableStateFlow(listOf("Salt", "Pepper", "Olive Oil", "Milk"))
    val pantry: StateFlow<List<String>> = _pantry.asStateFlow()

    private fun loadRecipes(): List<Recipe> {
        val json = prefs.getString(KEY_RECIPES, null)
        return if (json != null) {
            try {
                jsonToRecipes(json)
            } catch (e: Exception) {
                getMockRecipes()
            }
        } else {
            getMockRecipes()
        }
    }

    private fun loadCategories(): List<String> {
        val json = prefs.getString(KEY_CATEGORIES, null)
        val defaults = listOf("All", "Breakfast", "Dinner", "Healthy", "Dessert", "Quick", "Drinks")
        return if (json != null) {
            try {
                jsonToStringList(json)
            } catch (e: Exception) {
                defaults
            }
        } else {
            defaults
        }
    }

    private fun persistRecipes() {
        prefs.edit().putString(KEY_RECIPES, recipesToJson(_recipes.value)).apply()
    }

    private fun persistCategories() {
        prefs.edit().putString(KEY_CATEGORIES, stringListToJson(_categories.value)).apply()
    }

    fun updateSearch(query: String) {
        _searchQuery.value = query
    }

    fun toggleFavorite(recipeId: String) {
        _recipes.value = _recipes.value.map {
            if (it.id == recipeId) it.copy(isFavorite = !it.isFavorite) else it
        }
        persistRecipes()
    }

    fun addRecipe(recipe: Recipe) {
        _recipes.value = _recipes.value + recipe
        persistRecipes()
    }

    fun updateRecipe(recipe: Recipe) {
        _recipes.value = _recipes.value.map { if (it.id == recipe.id) recipe else it }
        persistRecipes()
    }

    fun addCategory(name: String) {
        val trimmed = name.trim()
        if (trimmed.isNotEmpty() && _categories.value.none { it.equals(trimmed, ignoreCase = true) }) {
            _categories.value = _categories.value + trimmed
            persistCategories()
        }
    }

    fun removeCategory(name: String) {
        if (name == "All") return
        _categories.value = _categories.value.filterNot { it == name }
        persistCategories()
    }

    fun setUserName(name: String) {
        _userName.value = name
        prefs.edit().putString(KEY_USER_NAME, name).apply()
    }

    fun setTimerSound(sound: TimerSound) {
        _selectedTimerSound.value = sound
        prefs.edit().putString(KEY_TIMER_SOUND, sound.name).apply()
    }

    val completionMessages = listOf(
        "Your kitchen must smell incredible right now.",
        "That aroma says dinner is almost ready.",
        "Time to plate your masterpiece.",
        "You've cooked something worth sharing.",
        "Freshly cooked always tastes better.",
        "The hardest part is waiting before the first bite.",
        "That looks restaurant-worthy.",
        "Your family is going to love this."
    )
}

// ============================================================================
// NOTIFICATION & SOUND HELPERS (for the cooking timer)
// ============================================================================

private const val TIMER_CHANNEL_ID = "timer_channel"
private const val TIMER_NOTIFICATION_ID = 1001

fun createTimerNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
            TIMER_CHANNEL_ID,
            "Cooking Timer",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Alerts you when a cooking timer finishes"
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }
}

@SuppressLint("MissingPermission")
fun showTimerFinishedNotification(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) {
        return
    }
    val notification = NotificationCompat.Builder(context, TIMER_CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_popup_reminder)
        .setContentTitle("Timer finished!")
        .setContentText("Your dish needs attention — swipe up on the step once you're done.")
        .setOngoing(true)
        .setAutoCancel(false)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .build()
    NotificationManagerCompat.from(context).notify(TIMER_NOTIFICATION_ID, notification)
}

fun dismissTimerNotification(context: Context) {
    NotificationManagerCompat.from(context).cancel(TIMER_NOTIFICATION_ID)
}

fun playTimerBeep(sound: TimerSound) {
    try {
        val toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
        toneGenerator.startTone(sound.toneType, sound.durationMs)
    } catch (e: Exception) {
        // Some devices don't support tone generation — fail silently
    }
}

// ============================================================================
// UNSPLASH IMAGE FETCHING (with Picsum fallback)
// ============================================================================

suspend fun fetchUnsplashImageUrl(query: String, accessKey: String): String? = withContext(Dispatchers.IO) {
    if (accessKey.isBlank()) return@withContext null
    try {
        val encodedQuery = Uri.encode(query)
        val url = URL("https://api.unsplash.com/search/photos?query=$encodedQuery&per_page=1&orientation=squarish&client_id=$accessKey")
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 8000
        connection.readTimeout = 8000
        if (connection.responseCode == HttpURLConnection.HTTP_OK) {
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()
            val json = JSONObject(response)
            val results = json.optJSONArray("results")
            if (results != null && results.length() > 0) {
                val urls = results.getJSONObject(0).getJSONObject("urls")
                val smallUrl = urls.optString("small", "")
                val regularUrl = urls.optString("regular", "")
                if (smallUrl.isNotEmpty()) smallUrl else if (regularUrl.isNotEmpty()) regularUrl else null
            } else {
                null
            }
        } else {
            connection.disconnect()
            null
        }
    } catch (e: Exception) {
        null
    }
}

@Composable
fun SmartImage(
    query: String,
    fallbackSeed: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    var resolvedUrl by remember(query) { mutableStateOf<String?>(null) }

    LaunchedEffect(query) {
        val fetched = fetchUnsplashImageUrl(query, BuildConfig.UNSPLASH_ACCESS_KEY)
        val safeSeed = fallbackSeed.filter { it.isLetterOrDigit() }.ifEmpty { "food" }
        resolvedUrl = fetched ?: "https://picsum.photos/seed/${Uri.encode(safeSeed)}/700/700"
    }

    Box(modifier = modifier) {
        val urlToShow = resolvedUrl
        if (urlToShow != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(urlToShow)
                    .crossfade(true)
                    .build(),
                contentDescription = query,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

// ============================================================================
// NAVIGATION & ROOT APP
// ============================================================================

@Composable
fun SmartRecipeApp(viewModel: RecipeViewModel = viewModel()) {
    val navController = rememberNavController()

    val currentRoute = navController.currentBackStackEntryFlow.collectAsState(initial = null).value?.destination?.route
    val backgroundThemes = remember {
        listOf(
            "cozy kitchen interior",
            "gourmet food photography",
            "chinese cuisine dishes",
            "malay food dishes",
            "restaurant interior design",
            "cafe aesthetic coffee"
        )
    }
    val backgroundQuery = remember(currentRoute) { backgroundThemes.random() }

    Box(modifier = Modifier.fillMaxSize()) {
        SmartImage(
            query = backgroundQuery,
            fallbackSeed = backgroundQuery,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.65f))
        )
        DecorativeBackground()

        NavHost(navController = navController, startDestination = "welcome") {
            composable("welcome") { WelcomeScreen(navController, viewModel) }
            composable("home") { HomeScreen(navController, viewModel) }
            composable("add_recipe") { RecipeFormScreen(navController, viewModel, existingRecipe = null) }
            composable("edit_recipe/{id}") { backStackEntry ->
                val id = backStackEntry.arguments?.getString("id")
                val recipe = viewModel.recipes.value.find { it.id == id }
                RecipeFormScreen(navController, viewModel, existingRecipe = recipe)
            }
            composable("settings") { SettingsScreen(navController, viewModel) }
            composable("recipe/{id}") { backStackEntry ->
                val id = backStackEntry.arguments?.getString("id")
                val recipe = viewModel.recipes.value.find { it.id == id }
                if (recipe != null) {
                    RecipeDetailScreen(navController, recipe, viewModel)
                }
            }
            composable("cooking_phase1/{id}") { backStackEntry ->
                val id = backStackEntry.arguments?.getString("id")
                val recipe = viewModel.recipes.value.find { it.id == id }
                if (recipe != null) {
                    Phase1GatherScreen(navController, recipe)
                }
            }
            composable("cooking_phase2/{id}") { backStackEntry ->
                val id = backStackEntry.arguments?.getString("id")
                val recipe = viewModel.recipes.value.find { it.id == id }
                if (recipe != null) {
                    Phase2CookingScreen(navController, recipe, viewModel)
                }
            }
        }
    }
}

// ============================================================================
// WELCOME SCREEN (name capture + animated greeting)
// ============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WelcomeScreen(navController: NavController, viewModel: RecipeViewModel) {
    val savedName by viewModel.userName.collectAsState()
    var nameInput by remember { mutableStateOf("") }
    var hasSubmitted by remember { mutableStateOf(savedName.isNotBlank()) }

    val displayTitle = if (savedName.isBlank()) "Chef" else "Chef, $savedName"

    val greetingMessage = remember(displayTitle, hasSubmitted) {
        listOf(
            "Good to see you, $displayTitle. What shall we cook today?",
            "Welcome back, $displayTitle — the kitchen has been waiting for you.",
            "Hello, $displayTitle. Let's create something wonderful together.",
            "It's always a pleasure, $displayTitle. Ready when you are.",
            "$displayTitle, your next delicious creation awaits."
        ).random()
    }

    LaunchedEffect(hasSubmitted) {
        if (hasSubmitted) {
            delay(2200)
            navController.navigate("home") {
                popUpTo("welcome") { inclusive = true }
            }
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "welcome_anim")
    val bounce by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -18f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse),
        label = "bounce"
    )
    val rotate by infiniteTransition.animateFloat(
        initialValue = -8f,
        targetValue = 8f,
        animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse),
        label = "rotate"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFFFFA45B), Color(0xFFFF6B9D), Color(0xFF6B5BFF))
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("🍰", "🌈", "🍜", "🍧").forEachIndexed { i, emoji ->
                    val direction = if (i % 2 == 0) 1f else -1f
                    Text(
                        emoji,
                        fontSize = 44.sp,
                        modifier = Modifier
                            .offset(y = (bounce * direction).dp)
                            .graphicsLayer { rotationZ = rotate * direction }
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            if (!hasSubmitted) {
                Text(
                    "Welcome! What should we call you?",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(20.dp))

                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    placeholder = { Text("Your name (optional)", color = Color.White.copy(alpha = 0.6f)) },
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(color = Color.White),
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        containerColor = Color.White.copy(alpha = 0.15f),
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.6f),
                        cursorColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth(0.85f)
                )

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = {
                        viewModel.setUserName(nameInput.trim())
                        hasSubmitted = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFFFF6B9D)),
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .height(52.dp)
                ) {
                    Text(
                        if (nameInput.isBlank()) "Continue as Guest" else "Continue",
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "Sign-in with Google is coming soon.",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.7f)
                )
            } else {
                Text(
                    greetingMessage,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

// ============================================================================
// DECORATIVE BACKGROUND ACCENTS
// ============================================================================

@Composable
fun DecorativeBackground() {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .size(260.dp)
                .align(Alignment.TopStart)
                .offset(x = (-60).dp, y = (-40).dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), Color.Transparent)
                    ),
                    shape = CircleShape
                )
        )
        Box(
            modifier = Modifier
                .size(320.dp)
                .align(Alignment.TopEnd)
                .offset(x = 80.dp, y = 480.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(MaterialTheme.colorScheme.secondary.copy(alpha = 0.30f), Color.Transparent)
                    ),
                    shape = CircleShape
                )
        )
        Box(
            modifier = Modifier
                .size(220.dp)
                .align(Alignment.BottomStart)
                .offset(x = (-40).dp, y = 60.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f), Color.Transparent)
                    ),
                    shape = CircleShape
                )
        )
    }
}

// ============================================================================
// HOME SCREEN
// ============================================================================

@Composable
fun HomeScreen(navController: NavController, viewModel: RecipeViewModel) {
    val recipes by viewModel.recipes.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val categories by viewModel.categories.collectAsState()
    var selectedCategory by remember { mutableStateOf("All") }
    var showCategoryDialog by remember { mutableStateOf(false) }

    val filteredRecipes = remember(recipes, searchQuery, selectedCategory) {
        recipes.filter { recipe ->
            val matchesCategory = selectedCategory == "All" ||
                recipe.category.equals(selectedCategory, ignoreCase = true)
            val query = searchQuery.trim()
            val matchesSearch = query.isEmpty() ||
                recipe.name.contains(query, ignoreCase = true) ||
                recipe.cuisine.contains(query, ignoreCase = true) ||
                recipe.category.contains(query, ignoreCase = true) ||
                recipe.ingredients.any { it.name.contains(query, ignoreCase = true) }
            matchesCategory && matchesSearch
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { navController.navigate("add_recipe") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Recipe")
            }
        }
    ) { padding ->
        LazyColumn(
            contentPadding = padding,
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                HomeHeader(
                    searchQuery = searchQuery,
                    onSearchChange = { viewModel.updateSearch(it) },
                    onSettingsClick = { navController.navigate("settings") }
                )
            }

            item {
                CategoryRow(
                    categories = categories,
                    selectedCategory = selectedCategory,
                    onCategorySelected = { selectedCategory = it },
                    onManageCategories = { showCategoryDialog = true }
                )
            }

            item {
                Text(
                    text = if (searchQuery.isNotBlank() || selectedCategory != "All") "Results" else "Recently Opened",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            if (filteredRecipes.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("🔍", fontSize = 40.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "No recipes match your search.",
                            color = Color.Gray,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                items(filteredRecipes.chunked(2)) { rowRecipes ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        for (recipe in rowRecipes) {
                            RecipeCard(
                                recipe = recipe,
                                modifier = Modifier.weight(1f),
                                onClick = { navController.navigate("recipe/${recipe.id}") },
                                onFavoriteClick = { viewModel.toggleFavorite(recipe.id) }
                            )
                        }
                        if (rowRecipes.size == 1) Spacer(modifier = Modifier.weight(1f))
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }

    if (showCategoryDialog) {
        CategoryManagerDialog(
            categories = categories,
            onAdd = { viewModel.addCategory(it) },
            onRemove = { viewModel.removeCategory(it) },
            onDismiss = { showCategoryDialog = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeHeader(searchQuery: String, onSearchChange: (String) -> Unit, onSettingsClick: () -> Unit) {
    Column(modifier = Modifier.padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "What would you like to cook today?",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onSettingsClick) {
                Icon(Icons.Default.Settings, contentDescription = "Settings")
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            placeholder = { Text("Search recipes, ingredients...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = TextFieldDefaults.outlinedTextFieldColors(
                containerColor = MaterialTheme.colorScheme.surface,
                unfocusedBorderColor = Color.Transparent
            )
        )
    }
}

@Composable
fun CategoryRow(
    categories: List<String>,
    selectedCategory: String,
    onCategorySelected: (String) -> Unit,
    onManageCategories: () -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(bottom = 16.dp)
    ) {
        items(categories) { cat ->
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = if (cat == selectedCategory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                contentColor = if (cat == selectedCategory) Color.White else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.clickable { onCategorySelected(cat) },
                shadowElevation = 2.dp
            ) {
                Text(
                    text = cat,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        item {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.clickable { onManageCategories() },
                shadowElevation = 2.dp
            ) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "Manage categories",
                    modifier = Modifier
                        .padding(10.dp)
                        .size(18.dp)
                )
            }
        }
    }
}

@Composable
fun CategoryManagerDialog(
    categories: List<String>,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var newCategoryText by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Manage Categories") },
        text = {
            Column {
                categories.filter { it != "All" }.forEach { cat ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(cat)
                        IconButton(onClick = { onRemove(cat) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Remove $cat")
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newCategoryText,
                        onValueChange = { newCategoryText = it },
                        placeholder = { Text("New category") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = {
                        if (newCategoryText.isNotBlank()) {
                            onAdd(newCategoryText.trim())
                            newCategoryText = ""
                        }
                    }) { Text("Add") }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        }
    )
}

@Composable
fun RecipeCard(
    recipe: Recipe,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onFavoriteClick: () -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column {
            Box {
                SmartImage(
                    query = recipe.name,
                    fallbackSeed = recipe.name,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                )
                IconButton(
                    onClick = onFavoriteClick,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .background(Color.White.copy(alpha = 0.7f), CircleShape)
                        .size(32.dp)
                ) {
                    Icon(
                        imageVector = if (recipe.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (recipe.isFavorite) Color.Red else Color.Gray,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = recipe.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.Gray)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("${recipe.prepTime + recipe.cookTime} min", fontSize = 12.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(recipe.difficulty, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ============================================================================
// RECIPE DETAILS SCREEN
// ============================================================================

@Composable
fun RecipeDetailScreen(navController: NavController, recipe: Recipe, viewModel: RecipeViewModel) {
    Scaffold(
        bottomBar = {
            Button(
                onClick = { navController.navigate("cooking_phase1/${recipe.id}") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .height(60.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
            ) {
                Text("Start Preparing", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        },
        containerColor = Color.Transparent
    ) { padding ->
        LazyColumn(contentPadding = padding) {
            item {
                Box {
                    SmartImage(
                        query = recipe.name,
                        fallbackSeed = recipe.name,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp)
                    )
                    IconButton(
                        onClick = { navController.popBackStack() },
                        modifier = Modifier
                            .padding(16.dp)
                            .background(Color.White.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                    IconButton(
                        onClick = { navController.navigate("edit_recipe/${recipe.id}") },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(16.dp)
                            .background(Color.White.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit recipe")
                    }
                }
            }
            item {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(recipe.name, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold)
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        StatBox(Icons.Default.Schedule, "Time", "${recipe.prepTime + recipe.cookTime}m")
                        StatBox(Icons.Default.Person, "Servings", "${recipe.servings}")
                        StatBox(Icons.Default.LocalFireDepartment, "Difficulty", recipe.difficulty)
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    Text("Ingredients", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))

                    recipe.ingredients.forEach { ingredient ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(ingredient.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            Text(ingredient.quantity, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatBox(icon: ImageVector, label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(value, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text(label, fontSize = 12.sp, color = Color.Gray)
    }
}

// ============================================================================
// RECIPE FORM SCREEN (Create AND Edit)
// ============================================================================

class IngredientFormRow(name: String = "", quantity: String = "") {
    var name by mutableStateOf(name)
    var quantity by mutableStateOf(quantity)
}

class StepFormRow(text: String = "") {
    var text by mutableStateOf(text)
}

@Composable
fun RecipeFormScreen(navController: NavController, viewModel: RecipeViewModel, existingRecipe: Recipe?) {
    val categories by viewModel.categories.collectAsState()
    val selectableCategories = categories.filter { it != "All" }
    val isEditing = existingRecipe != null

    var name by remember { mutableStateOf(existingRecipe?.name ?: "") }
    var selectedCategory by remember {
        mutableStateOf(existingRecipe?.category ?: selectableCategories.firstOrNull() ?: "Dinner")
    }
    var newCategoryText by remember { mutableStateOf("") }
    var prepTime by remember { mutableStateOf((existingRecipe?.prepTime ?: 10).toString()) }
    var cookTime by remember { mutableStateOf((existingRecipe?.cookTime ?: 15).toString()) }
    var servings by remember { mutableStateOf((existingRecipe?.servings ?: 2).toString()) }
    var difficulty by remember { mutableStateOf(existingRecipe?.difficulty ?: "Easy") }

    val ingredientRows = remember {
        mutableStateListOf<IngredientFormRow>().apply {
            if (existingRecipe != null && existingRecipe.ingredients.isNotEmpty()) {
                existingRecipe.ingredients.forEach { add(IngredientFormRow(it.name, it.quantity)) }
            } else {
                add(IngredientFormRow())
            }
        }
    }
    val stepRows = remember {
        mutableStateListOf<StepFormRow>().apply {
            if (existingRecipe != null && existingRecipe.steps.isNotEmpty()) {
                existingRecipe.steps.forEach { add(StepFormRow(it)) }
            } else {
                add(StepFormRow())
            }
        }
    }

    Scaffold(containerColor = Color.Transparent) { padding ->
        LazyColumn(
            contentPadding = padding,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                    Text(
                        if (isEditing) "Edit Recipe" else "New Recipe",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Recipe name") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))

                Text("Category", fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(selectableCategories) { cat ->
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = if (cat == selectedCategory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                            contentColor = if (cat == selectedCategory) Color.White else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.clickable { selectedCategory = cat }
                        ) {
                            Text(cat, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newCategoryText,
                        onValueChange = { newCategoryText = it },
                        placeholder = { Text("Add a new category") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = {
                        if (newCategoryText.isNotBlank()) {
                            viewModel.addCategory(newCategoryText.trim())
                            selectedCategory = newCategoryText.trim()
                            newCategoryText = ""
                        }
                    }) { Text("Add") }
                }

                Spacer(modifier = Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = prepTime,
                        onValueChange = { prepTime = it.filter { c -> c.isDigit() } },
                        label = { Text("Prep (min)") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = cookTime,
                        onValueChange = { cookTime = it.filter { c -> c.isDigit() } },
                        label = { Text("Cook (min)") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = servings,
                        onValueChange = { servings = it.filter { c -> c.isDigit() } },
                        label = { Text("Servings") },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text("Difficulty", fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Easy", "Medium", "Hard").forEach { level ->
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = if (level == difficulty) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surface,
                            contentColor = if (level == difficulty) Color.White else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.clickable { difficulty = level }
                        ) {
                            Text(level, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Ingredients", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    TextButton(onClick = { ingredientRows.add(IngredientFormRow()) }) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add")
                    }
                }
            }

            items(ingredientRows) { row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = row.name,
                        onValueChange = { row.name = it },
                        placeholder = { Text("Ingredient") },
                        modifier = Modifier.weight(2f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = row.quantity,
                        onValueChange = { row.quantity = it },
                        placeholder = { Text("Qty") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    IconButton(onClick = { ingredientRows.remove(row) }, enabled = ingredientRows.size > 1) {
                        Icon(Icons.Default.Close, contentDescription = "Remove ingredient")
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Steps", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    TextButton(onClick = { stepRows.add(StepFormRow()) }) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add")
                    }
                }
            }

            itemsIndexed(stepRows) { index, row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("${index + 1}.", modifier = Modifier.padding(top = 16.dp))
                    OutlinedTextField(
                        value = row.text,
                        onValueChange = { row.text = it },
                        placeholder = { Text("Describe this step") },
                        modifier = Modifier.weight(1f),
                        minLines = 2
                    )
                    IconButton(onClick = { stepRows.remove(row) }, enabled = stepRows.size > 1) {
                        Icon(Icons.Default.Close, contentDescription = "Remove step")
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(28.dp))
                Button(
                    onClick = {
                        val finalIngredients = ingredientRows
                            .filter { it.name.isNotBlank() }
                            .map { Ingredient(it.name.trim(), it.quantity.ifBlank { "as needed" }) }
                        val finalSteps = stepRows
                            .map { it.text.trim() }
                            .filter { it.isNotBlank() }

                        if (name.isNotBlank() && finalIngredients.isNotEmpty() && finalSteps.isNotEmpty()) {
                            val safeSeed = name.trim().filter { it.isLetterOrDigit() }.ifEmpty { "recipe" }
                            if (isEditing && existingRecipe != null) {
                                val updated = existingRecipe.copy(
                                    name = name.trim(),
                                    cuisine = selectedCategory,
                                    category = selectedCategory,
                                    prepTime = prepTime.toIntOrNull() ?: 10,
                                    cookTime = cookTime.toIntOrNull() ?: 15,
                                    servings = servings.toIntOrNull() ?: 2,
                                    difficulty = difficulty,
                                    ingredients = finalIngredients,
                                    steps = finalSteps
                                )
                                viewModel.updateRecipe(updated)
                            } else {
                                val newRecipe = Recipe(
                                    id = "recipe_${System.currentTimeMillis()}",
                                    name = name.trim(),
                                    cuisine = selectedCategory,
                                    category = selectedCategory,
                                    prepTime = prepTime.toIntOrNull() ?: 10,
                                    cookTime = cookTime.toIntOrNull() ?: 15,
                                    servings = servings.toIntOrNull() ?: 2,
                                    difficulty = difficulty,
                                    imageUrl = "https://picsum.photos/seed/${Uri.encode(safeSeed)}/800/600",
                                    ingredients = finalIngredients,
                                    steps = finalSteps
                                )
                                viewModel.addRecipe(newRecipe)
                            }
                            navController.popBackStack()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(if (isEditing) "Save Changes" else "Save Recipe", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

// ============================================================================
// SETTINGS SCREEN
// ============================================================================

@Composable
fun SettingsScreen(navController: NavController, viewModel: RecipeViewModel) {
    val selectedSound by viewModel.selectedTimerSound.collectAsState()
    val userName by viewModel.userName.collectAsState()
    var nameEdit by remember(userName) { mutableStateOf(userName) }

    Scaffold(containerColor = Color.Transparent) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 16.dp)) {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
                Text("Settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }

            Text("Your name", fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = nameEdit,
                    onValueChange = { nameEdit = it },
                    placeholder = { Text("Chef") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(onClick = { viewModel.setUserName(nameEdit.trim()) }) { Text("Save") }
            }

            Spacer(modifier = Modifier.height(28.dp))
            Text("Timer alarm sound", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))

            TimerSound.values().forEach { sound ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.setTimerSound(sound) }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = sound == selectedSound,
                        onClick = { viewModel.setTimerSound(sound) }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(sound.label, modifier = Modifier.weight(1f))
                    TextButton(onClick = { playTimerBeep(sound) }) {
                        Text("Preview")
                    }
                }
            }
        }
    }
}

// ============================================================================
// PHASE 1: INGREDIENT GATHERING (NATIVE SWIPE PHYSICS)
// ============================================================================

@Composable
fun Phase1GatherScreen(navController: NavController, recipe: Recipe) {
    var currentIndex by remember { mutableStateOf(0) }
    val missingIngredients = remember { mutableStateListOf<Ingredient>() }
    var confirmedGathered by remember { mutableStateOf(false) }

    if (currentIndex >= recipe.ingredients.size) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (missingIngredients.isEmpty()) {
                Text("✅", fontSize = 64.sp)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "You've got everything!",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center
                )
                Text("No shopping needed — you're ready to cook.", color = Color.Gray, textAlign = TextAlign.Center)
            } else {
                Text("🛒", fontSize = 56.sp)
                Spacer(modifier = Modifier.height(12.dp))
                Text("You still need to get:", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                Spacer(modifier = Modifier.height(12.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                ) {
                    missingIngredients.forEach { ing ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(ing.name, fontWeight = FontWeight.SemiBold)
                            Text(ing.quantity, color = Color.Gray)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { confirmedGathered = !confirmedGathered },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = confirmedGathered, onCheckedChange = { confirmedGathered = it })
                    Text("I've gathered all of these already")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = { navController.navigate("cooking_phase2/${recipe.id}") { popUpTo("home") } },
                enabled = missingIngredients.isEmpty() || confirmedGathered,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Start Cooking!", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
        return
    }

    val currentIngredient = recipe.ingredients[currentIndex]

    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Gather Ingredients", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                "Swipe UP if you already have it • DOWN if you need to buy it",
                fontSize = 14.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center
            )
            Text(
                "ITEM ${currentIndex + 1} OF ${recipe.ingredients.size}",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            SwipeableCard(
                key = currentIngredient.name,
                onSwipedUp = { currentIndex++ },
                onSwipedDown = {
                    missingIngredients.add(currentIngredient)
                    currentIndex++
                }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    SmartImage(
                        query = currentIngredient.name,
                        fallbackSeed = currentIngredient.name,
                        modifier = Modifier
                            .size(120.dp)
                            .clip(RoundedCornerShape(20.dp))
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = currentIngredient.name,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = currentIngredient.quantity,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.Gray
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .clip(RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.BottomCenter
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(bottom = 16.dp)) {
                LazyRow(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(missingIngredients) { ing ->
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                ing.name,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text("SHOPPING LIST", fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
            }
        }
    }
}

// ============================================================================
// PHASE 2: COOKING STEPS & TIMERS
// ============================================================================

@Composable
fun Phase2CookingScreen(navController: NavController, recipe: Recipe, viewModel: RecipeViewModel) {
    var currentStepIndex by remember { mutableStateOf(0) }
    val context = LocalContext.current

    if (currentStepIndex >= recipe.steps.size) {
        val message = remember { viewModel.completionMessages.random() }
        DishCompleteScreen(navController = navController, message = message)
        return
    }

    val stepText = recipe.steps[currentStepIndex]
    val detectedSeconds = extractTimeInSeconds(stepText)
    val selectedTimerSound by viewModel.selectedTimerSound.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Cooking Steps", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Swipe UP for next step", fontSize = 14.sp, color = Color.Gray)
            Text(
                "STEP ${currentStepIndex + 1} OF ${recipe.steps.size}",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            SwipeableCard(
                key = stepText,
                swipeDownEnabled = false,
                onSwipedUp = {
                    dismissTimerNotification(context)
                    currentStepIndex++
                },
                onSwipedDown = { }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("${currentStepIndex + 1}", color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = stepText,
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                        lineHeight = 28.sp
                    )

                    if (detectedSeconds > 0) {
                        Spacer(modifier = Modifier.weight(1f))
                        TimerWidget(seconds = detectedSeconds, timerSound = selectedTimerSound)
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(48.dp))
    }
}

@Composable
fun DishCompleteScreen(navController: NavController, message: String) {
    var scale by remember { mutableStateOf(0.3f) }
    val animatedScale by animateFloatAsState(
        targetValue = scale,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "dish_complete_scale"
    )
    LaunchedEffect(Unit) { scale = 1f }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFFFF6B6B),
                        Color(0xFFFFD93D),
                        Color(0xFF6BCB77),
                        Color(0xFF4D96FF)
                    )
                )
            )
    ) {
        Text("🎊", fontSize = 34.sp, modifier = Modifier.align(Alignment.TopStart).padding(24.dp).graphicsLayer { rotationZ = -15f })
        Text("✨", fontSize = 28.sp, modifier = Modifier.align(Alignment.TopEnd).padding(32.dp))
        Text("🎈", fontSize = 30.sp, modifier = Modifier.align(Alignment.BottomStart).padding(28.dp).graphicsLayer { rotationZ = 10f })
        Text("🎉", fontSize = 36.sp, modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp).graphicsLayer { rotationZ = -8f })
        Text("⭐", fontSize = 24.sp, modifier = Modifier.align(Alignment.CenterStart).padding(16.dp))
        Text("🍾", fontSize = 26.sp, modifier = Modifier.align(Alignment.CenterEnd).padding(16.dp).graphicsLayer { rotationZ = 12f })

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.85f)
                .graphicsLayer { scaleX = animatedScale; scaleY = animatedScale }
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("🎉", fontSize = 80.sp)
            Spacer(modifier = Modifier.height(24.dp))
            Text("Dish Complete!", style = MaterialTheme.typography.headlineLarge, color = Color.White, fontWeight = FontWeight.ExtraBold)
            Text(
                message,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = 0.95f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp, bottom = 16.dp)
            )
            Button(
                onClick = { navController.navigate("home") { popUpTo(0) } },
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFFFF6B6B)),
                modifier = Modifier.height(56.dp)
            ) {
                Text("Cook Another Dish", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}

@Composable
fun TimerWidget(seconds: Int, timerSound: TimerSound) {
    var timeRemaining by remember { mutableStateOf(seconds) }
    var isRunning by remember { mutableStateOf(false) }

    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Timer still works even if the user declines */ }

    LaunchedEffect(Unit) {
        createTimerNotificationChannel(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(isRunning) {
        if (isRunning) {
            while (timeRemaining > 0) {
                delay(1000)
                timeRemaining--
            }
            isRunning = false
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            repeat(3) {
                playTimerBeep(timerSound)
                delay(350)
            }
            showTimerFinishedNotification(context)
        }
    }

    val minutes = timeRemaining / 60
    val secs = timeRemaining % 60
    val timeString = String.format("%02d:%02d", minutes, secs)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isRunning && timeRemaining > 0) {
                isRunning = true
            },
        colors = CardDefaults.cardColors(
            containerColor = if (timeRemaining == 0) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
        ),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("TIMER DETECTED", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp)
            Text(timeString, fontSize = 48.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(vertical = 8.dp))
            Text(
                if (timeRemaining == 0) "Time's Up! Swipe up when ready." else if (isRunning) "Running..." else "Tap to start",
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

fun extractTimeInSeconds(text: String): Int {
    val regex = Regex("(?:(\\d+)|(one|two|three|four|five|six|seven|eight|nine|ten))\\s*(minute|min|second|sec)s?", RegexOption.IGNORE_CASE)
    val match = regex.find(text) ?: return 0

    val numStr = match.groupValues[1].ifEmpty { match.groupValues[2] }
    val unit = match.groupValues[3].lowercase()

    val numMap = mapOf("one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10)
    val num = numStr.toIntOrNull() ?: numMap[numStr.lowercase()] ?: 0

    return if (unit.startsWith("min")) num * 60 else num
}

// ============================================================================
// CORE MECHANICS: NATIVE SWIPEABLE CARD WITH SPRING PHYSICS
// ============================================================================

@Composable
fun SwipeableCard(
    key: Any,
    swipeDownEnabled: Boolean = true,
    onSwipedUp: () -> Unit,
    onSwipedDown: () -> Unit,
    content: @Composable () -> Unit
) {
    val haptic = LocalHapticFeedback.current

    val offsetY = remember(key) { Animatable(0f) }
    val rotateZ = remember(key) { Animatable(0f) }
    val scale = remember(key) { Animatable(1f) }
    val alpha = remember(key) { Animatable(1f) }
    val scope = rememberCoroutineScope()

    val screenHeight = 2000f
    val swipeThreshold = 300f

    Card(
        modifier = Modifier
            .width(300.dp)
            .height(420.dp)
            .offset { IntOffset(0, offsetY.value.roundToInt()) }
            .graphicsLayer {
                rotationZ = rotateZ.value
                scaleX = scale.value
                scaleY = scale.value
                this.alpha = alpha.value
            }
            .pointerInput(key) {
                detectDragGestures(
                    onDragEnd = {
                        scope.launch {
                            if (offsetY.value < -swipeThreshold) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                launch { rotateZ.animateTo(15f, tween(300)) }
                                launch { scale.animateTo(0.5f, tween(300)) }
                                offsetY.animateTo(-screenHeight, tween(300, easing = FastOutLinearInEasing))
                                onSwipedUp()
                            } else if (swipeDownEnabled && offsetY.value > swipeThreshold) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                launch { rotateZ.animateTo(-20f, tween(350)) }
                                launch { scale.animateTo(0.1f, tween(350, easing = FastOutLinearInEasing)) }
                                launch { alpha.animateTo(0f, tween(350)) }
                                offsetY.animateTo(650f, tween(350, easing = FastOutLinearInEasing))
                                onSwipedDown()
                            } else {
                                launch { rotateZ.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy)) }
                                offsetY.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
                            }
                        }
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        scope.launch {
                            val newY = offsetY.value + dragAmount.y
                            val resistance = if (newY < 0 || (newY > 0 && swipeDownEnabled)) 1f else 0.3f
                            offsetY.snapTo(offsetY.value + (dragAmount.y * resistance))
                            rotateZ.snapTo(offsetY.value * 0.02f)
                        }
                    }
                )
            },
        shape = RoundedCornerShape(32.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        content = { content() }
    )
}

// ============================================================================
// MOCK DATA GENERATOR (default recipes — fully editable via Edit screen)
// ============================================================================

fun getMockRecipes(): List<Recipe> {
    return listOf(
        Recipe(
            id = "1",
            name = "Mango Lassi",
            cuisine = "Drinks",
            category = "Drinks",
            prepTime = 5,
            cookTime = 0,
            servings = 2,
            difficulty = "Easy",
            imageUrl = "https://picsum.photos/seed/mangolassi/800/600",
            ingredients = listOf(
                Ingredient("Ripe Mangoes", "2 pcs"),
                Ingredient("Plain Yogurt", "1 cup"),
                Ingredient("Milk", "1/2 cup"),
                Ingredient("Sugar", "2 tbsp"),
                Ingredient("Cardamom Powder", "a pinch")
            ),
            steps = listOf(
                "Peel and chop the mangoes into chunks.",
                "Add mango, yogurt, milk, and sugar to a blender.",
                "Blend until smooth and creamy.",
                "Add a pinch of cardamom powder and blend again briefly.",
                "Pour into glasses and chill for 10 minutes before serving."
            )
        ),
        Recipe(
            id = "2",
            name = "Classic Banana Bread",
            cuisine = "Dessert",
            category = "Dessert",
            prepTime = 15,
            cookTime = 60,
            servings = 8,
            difficulty = "Easy",
            imageUrl = "https://picsum.photos/seed/bananabread/800/600",
            ingredients = listOf(
                Ingredient("Ripe Bananas", "3 pcs"),
                Ingredient("All-Purpose Flour", "1 3/4 cup"),
                Ingredient("Sugar", "3/4 cup"),
                Ingredient("Butter", "1/3 cup"),
                Ingredient("Eggs", "1 pc"),
                Ingredient("Baking Soda", "1 tsp"),
                Ingredient("Salt", "a pinch")
            ),
            steps = listOf(
                "Preheat the oven to 350°F (175°C) and grease a loaf pan.",
                "Mash the ripe bananas in a large bowl.",
                "Mix in melted butter, sugar, egg, and vanilla.",
                "Sprinkle baking soda and salt over the mixture, then stir in the flour.",
                "Pour the batter into the loaf pan and bake for 60 minutes until golden."
            )
        )
    )
}
