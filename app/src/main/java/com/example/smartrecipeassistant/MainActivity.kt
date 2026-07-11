package com.example.smartrecipeassistant

import android.Manifest
import android.annotation.SuppressLint
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.*
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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
// VIEW MODEL (Presentation & State Management)
// ============================================================================

class RecipeViewModel : ViewModel() {
    private val _recipes = MutableStateFlow(getMockRecipes())
    val recipes: StateFlow<List<Recipe>> = _recipes.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _categories = MutableStateFlow(
        listOf("All", "Breakfast", "Dinner", "Healthy", "Dessert", "Quick")
    )
    val categories: StateFlow<List<String>> = _categories.asStateFlow()

    private val _pantry = MutableStateFlow(listOf("Salt", "Pepper", "Olive Oil", "Milk"))
    val pantry: StateFlow<List<String>> = _pantry.asStateFlow()

    fun updateSearch(query: String) {
        _searchQuery.value = query
    }

    fun toggleFavorite(recipeId: String) {
        _recipes.value = _recipes.value.map {
            if (it.id == recipeId) it.copy(isFavorite = !it.isFavorite) else it
        }
    }

    fun addRecipe(recipe: Recipe) {
        _recipes.value = _recipes.value + recipe
    }

    fun addCategory(name: String) {
        val trimmed = name.trim()
        if (trimmed.isNotEmpty() && _categories.value.none { it.equals(trimmed, ignoreCase = true) }) {
            _categories.value = _categories.value + trimmed
        }
    }

    fun removeCategory(name: String) {
        if (name == "All") return
        _categories.value = _categories.value.filterNot { it == name }
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

fun playTimerBeep() {
    try {
        val toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
        toneGenerator.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 200)
    } catch (e: Exception) {
        // Some devices don't support tone generation — fail silently
    }
}

// ============================================================================
// NAVIGATION & ROOT APP
// ============================================================================

@Composable
fun SmartRecipeApp(viewModel: RecipeViewModel = viewModel()) {
    val navController = rememberNavController()

    val currentRoute = navController.currentBackStackEntryFlow.collectAsState(initial = null).value?.destination?.route
    val backgroundSeed = when {
        currentRoute?.startsWith("cooking") == true -> "cookingphase"
        currentRoute == "home" -> "kitcheninterior"
        currentRoute == "splash" -> "warmkitchen"
        currentRoute == "add_recipe" -> "freshingredients"
        else -> "fooddark"
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data("https://picsum.photos/seed/$backgroundSeed/1080/1920")
                .crossfade(true)
                .build(),
            contentDescription = "Background",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.65f))
        )
        DecorativeBackground()

        NavHost(navController = navController, startDestination = "splash") {
            composable("splash") { SplashScreen(navController) }
            composable("home") { HomeScreen(navController, viewModel) }
            composable("add_recipe") { AddRecipeScreen(navController, viewModel) }
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
                    Phase2CookingScreen(navController, recipe, viewModel.completionMessages)
                }
            }
        }
    }
}

// ============================================================================
// SPLASH SCREEN
// ============================================================================

@Composable
fun SplashScreen(navController: NavController) {
    val greetings = remember {
        listOf(
            "Good to see you, Yan. What shall we cook today?",
            "Welcome back, Yan — the kitchen has been waiting for you.",
            "Hello, Yan. Let's create something wonderful together.",
            "It's always a pleasure, Yan. Ready when you are.",
            "Yan, your next delicious creation awaits."
        )
    }
    val message = remember { greetings.random() }

    LaunchedEffect(Unit) {
        delay(2200)
        navController.navigate("home") {
            popUpTo("splash") { inclusive = true }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.background)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Text("🍲", fontSize = 56.sp)
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                message,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )
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
                HomeHeader(searchQuery) { viewModel.updateSearch(it) }
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
fun HomeHeader(searchQuery: String, onSearchChange: (String) -> Unit) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            text = "What would you like to cook today?",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface
        )
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
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(recipe.imageUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = recipe.name,
                    contentScale = ContentScale.Crop,
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
                    AsyncImage(
                        model = recipe.imageUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
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
// ADD RECIPE SCREEN
// ============================================================================

class IngredientFormRow(name: String = "", quantity: String = "") {
    var name by mutableStateOf(name)
    var quantity by mutableStateOf(quantity)
}

class StepFormRow(text: String = "") {
    var text by mutableStateOf(text)
}

@Composable
fun AddRecipeScreen(navController: NavController, viewModel: RecipeViewModel) {
    val categories by viewModel.categories.collectAsState()
    val selectableCategories = categories.filter { it != "All" }

    var name by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(selectableCategories.firstOrNull() ?: "Dinner") }
    var newCategoryText by remember { mutableStateOf("") }
    var prepTime by remember { mutableStateOf("10") }
    var cookTime by remember { mutableStateOf("15") }
    var servings by remember { mutableStateOf("2") }
    var difficulty by remember { mutableStateOf("Easy") }

    val ingredientRows = remember { mutableStateListOf(IngredientFormRow()) }
    val stepRows = remember { mutableStateListOf(StepFormRow()) }

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
                    Text("New Recipe", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
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
                            val newRecipe = Recipe(
                                id = "recipe_${System.currentTimeMillis()}",
                                name = name.trim(),
                                cuisine = selectedCategory,
                                category = selectedCategory,
                                prepTime = prepTime.toIntOrNull() ?: 10,
                                cookTime = cookTime.toIntOrNull() ?: 15,
                                servings = servings.toIntOrNull() ?: 2,
                                difficulty = difficulty,
                                imageUrl = "https://picsum.photos/seed/${Uri.encode(name.trim().filter { it.isLetterOrDigit() }.ifEmpty { "recipe" })}/800/600",
                                ingredients = finalIngredients,
                                steps = finalSteps
                            )
                            viewModel.addRecipe(newRecipe)
                            navController.popBackStack()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Save Recipe", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(24.dp))
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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
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
                modifier = Modifier.padding(top = 8.dp)
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
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("🍋", fontSize = 48.sp)
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
fun Phase2CookingScreen(navController: NavController, recipe: Recipe, completionMessages: List<String>) {
    var currentStepIndex by remember { mutableStateOf(0) }
    val context = LocalContext.current

    if (currentStepIndex >= recipe.steps.size) {
        val message = remember { completionMessages.random() }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.secondary),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("🎉", fontSize = 80.sp)
            Spacer(modifier = Modifier.height(24.dp))
            Text("Dish Complete!", style = MaterialTheme.typography.headlineLarge, color = Color.White, fontWeight = FontWeight.ExtraBold)
            Text(message, style = MaterialTheme.typography.titleMedium, color = Color.White.copy(alpha = 0.9f), textAlign = TextAlign.Center, modifier = Modifier.padding(32.dp))

            Button(
                onClick = { navController.navigate("home") { popUpTo(0) } },
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = MaterialTheme.colorScheme.secondary),
                modifier = Modifier.padding(top = 32.dp).height(56.dp)
            ) {
                Text("Cook Another Dish", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
        return
    }

    val stepText = recipe.steps[currentStepIndex]
    val detectedSeconds = extractTimeInSeconds(stepText)

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Cooking Steps", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Swipe UP for next step", fontSize = 14.sp, color = Color.Gray)
            Text(
                "STEP ${currentStepIndex + 1} OF ${recipe.steps.size}",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 8.dp)
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
                        TimerWidget(seconds = detectedSeconds)
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(48.dp))
    }
}

@Composable
fun TimerWidget(seconds: Int) {
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
                playTimerBeep()
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
                                // Swipe DOWN — shrinks and fades toward the basket below
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
// MOCK DATA GENERATOR
// ============================================================================

fun getMockRecipes(): List<Recipe> {
    return listOf(
        Recipe(
            id = "1",
            name = "Creamy Mashed Potatoes",
            cuisine = "American",
            category = "Side Dish",
            prepTime = 10,
            cookTime = 15,
            servings = 4,
            difficulty = "Easy",
            imageUrl = "https://images.unsplash.com/photo-1518977676601-b53f82aba655?q=80&w=800",
            ingredients = listOf(
                Ingredient("Large Potatoes", "4 pcs"),
                Ingredient("Whole Milk", "1/2 cup"),
                Ingredient("Butter", "4 tbsp"),
                Ingredient("Salt & Pepper", "to taste")
            ),
            steps = listOf(
                "Wash and peel the potatoes thoroughly.",
                "Boil the potatoes for 15 minutes until soft.",
                "Mash the potatoes while they are hot. Tip: Use a ricer for a smoother texture.",
                "Stir in milk and butter. Let it sit for 2 mins to absorb flavors.",
                "Season with salt and pepper to taste."
            )
        ),
        Recipe(
            id = "2",
            name = "Garlic Herb Steak",
            cuisine = "French",
            category = "Dinner",
            prepTime = 15,
            cookTime = 10,
            servings = 2,
            difficulty = "Medium",
            imageUrl = "https://images.unsplash.com/photo-1600891964092-4316c288032e?q=80&w=800",
            ingredients = listOf(
                Ingredient("Ribeye Steak", "2 pcs"),
                Ingredient("Garlic", "4 cloves"),
                Ingredient("Rosemary", "2 sprigs"),
                Ingredient("Butter", "3 tbsp")
            ),
            steps = listOf(
                "Let steak rest at room temp for 30 minutes.",
                "Season generously with salt and pepper.",
                "Sear in a very hot cast iron skillet for 3 minutes per side.",
                "Add butter, garlic, and rosemary. Baste the steak for 2 minutes.",
                "Rest the meat for 5 minutes before slicing."
            ),
            isFavorite = true
        )
    )
}
