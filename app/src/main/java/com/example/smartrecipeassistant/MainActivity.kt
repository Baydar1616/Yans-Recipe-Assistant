package com.example.smartrecipeassistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random

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
    val prepTime: Int, // minutes
    val cookTime: Int, // minutes
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
    // In a production app, this would be backed by Room Database & WorkManager
    private val _recipes = MutableStateFlow(getMockRecipes())
    val recipes: StateFlow<List<Recipe>> = _recipes.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

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

    // Dynamic Compliments for Completion Screen
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
// NAVIGATION & ROOT APP
// ============================================================================

@Composable
fun SmartRecipeApp(viewModel: RecipeViewModel = viewModel()) {
    val navController = rememberNavController()
    
    // Dynamic Unsplash Background Management
    val currentRoute = navController.currentBackStackEntryFlow.collectAsState(initial = null).value?.destination?.route
    val backgroundCategory = when {
        currentRoute?.startsWith("cooking") == true -> "cooking,ingredients"
        currentRoute == "home" -> "kitchen,interior"
        else -> "food,dark"
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // App-wide Dynamic Background
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data("https://source.unsplash.com/featured/?$backgroundCategory")
                .crossfade(true)
                .build(),
            contentDescription = "Background",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        // Overlay to ensure text readability
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.90f))
        )

        NavHost(navController = navController, startDestination = "home") {
            composable("home") { HomeScreen(navController, viewModel) }
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
// HOME SCREEN
// ============================================================================

@Composable
fun HomeScreen(navController: NavController, viewModel: RecipeViewModel) {
    val recipes by viewModel.recipes.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    Scaffold(
        containerColor = Color.Transparent,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { /* TODO: Navigate to Create Recipe */ },
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
            
            item { CategoryRow() }

            item {
                Text(
                    "Recently Opened",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            items(recipes.chunked(2)) { rowRecipes ->
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
                    // Fill empty space if odd number
                    if (rowRecipes.size == 1) Spacer(modifier = Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
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
fun CategoryRow() {
    val categories = listOf("All", "Breakfast", "Dinner", "Healthy", "Dessert", "Quick")
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(bottom = 16.dp)
    ) {
        items(categories) { cat ->
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = if (cat == "All") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                contentColor = if (cat == "All") Color.White else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.clickable { /* Select category */ },
                shadowElevation = 2.dp
            ) {
                Text(
                    text = cat,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
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
                    
                    // Stats Row
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
// PHASE 1: INGREDIENT GATHERING (NATIVE SWIPE PHYSICS)
// ============================================================================

@Composable
fun Phase1GatherScreen(navController: NavController, recipe: Recipe) {
    var currentIndex by remember { mutableStateOf(0) }
    val collectedIngredients = remember { mutableStateListOf<Ingredient>() }

    if (currentIndex >= recipe.ingredients.size) {
        // Confirmation View
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("🛒", fontSize = 64.sp)
            Spacer(modifier = Modifier.height(16.dp))
            Text("Basket Confirmed!", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
            Text("You collected ${collectedIngredients.size} items.", color = Color.Gray)
            
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = { navController.navigate("cooking_phase2/${recipe.id}") { popUpTo("home") } },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Yes, Start Cooking!", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
        return
    }

    val currentIngredient = recipe.ingredients[currentIndex]

    Column(modifier = Modifier.fillMaxSize()) {
        // Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Gather Ingredients", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Swipe UP to collect • DOWN to discard", fontSize = 14.sp, color = Color.Gray)
            Text(
                "ITEM ${currentIndex + 1} OF ${recipe.ingredients.size}",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        // Swipeable Area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            SwipeableCard(
                key = currentIngredient.name, // Forces recomposition on new item
                onSwipedUp = {
                    collectedIngredients.add(currentIngredient)
                    currentIndex++
                },
                onSwipedDown = {
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

        // Basket UI
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .clip(RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.BottomCenter
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(bottom = 16.dp)) {
                // Chips
                LazyRow(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(collectedIngredients) { ing ->
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
                Text("YOUR BASKET", fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
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

    if (currentStepIndex >= recipe.steps.size) {
        // Done Screen
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
                swipeDownEnabled = false, // Only swipe up to proceed
                onSwipedUp = { currentStepIndex++ },
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

    LaunchedEffect(isRunning) {
        if (isRunning) {
            while (timeRemaining > 0) {
                delay(1000)
                timeRemaining--
            }
            isRunning = false
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            // In a real app, trigger Notification/AlarmManager here
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
                if (timeRemaining == 0) "Time's Up!" else if (isRunning) "Running..." else "Tap to start",
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

// Regex Time Extractor
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
    
    // Animatable states for precise physics control
    val offsetY = remember(key) { Animatable(0f) }
    val rotateZ = remember(key) { Animatable(0f) }
    val scale = remember(key) { Animatable(1f) }
    val scope = rememberCoroutineScope()

    val screenHeight = 2000f // Arbitrary large boundary for throw off-screen
    val swipeThreshold = 300f // Pixels required to commit to a swipe

    Card(
        modifier = Modifier
            .width(300.dp)
            .height(420.dp)
            .offset { IntOffset(0, offsetY.value.roundToInt()) }
            .graphicsLayer {
                rotationZ = rotateZ.value
                scaleX = scale.value
                scaleY = scale.value
            }
            .pointerInput(key) {
                detectDragGestures(
                    onDragEnd = {
                        scope.launch {
                            if (offsetY.value < -swipeThreshold) {
                                // Swipe UP Commit
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                launch { rotateZ.animateTo(15f, tween(300)) }
                                launch { scale.animateTo(0.5f, tween(300)) }
                                offsetY.animateTo(-screenHeight, tween(300, easing = FastOutLinearInEasing))
                                onSwipedUp()
                            } else if (swipeDownEnabled && offsetY.value > swipeThreshold) {
                                // Swipe DOWN Commit
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                launch { rotateZ.animateTo(-15f, tween(300)) }
                                launch { scale.animateTo(0.8f, tween(300)) }
                                offsetY.animateTo(screenHeight, tween(300, easing = FastOutLinearInEasing))
                                onSwipedDown()
                            } else {
                                // Snap back with Spring Physics
                                launch { rotateZ.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy)) }
                                offsetY.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
                            }
                        }
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        scope.launch {
                            val newY = offsetY.value + dragAmount.y
                            // Add resistance to drag
                            val resistance = if (newY < 0 || (newY > 0 && swipeDownEnabled)) 1f else 0.3f
                            offsetY.snapTo(offsetY.value + (dragAmount.y * resistance))
                            
                            // Slight rotation based on drag
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
            imageUrl = "https://images.unsplash.com/photo-1518977676601-b53f82aba655?q=80&w=800", // Unsplash static demo
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
