package au.howesthat.whirl

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import au.howesthat.whirl.ui.theme.WhirlTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WhirlTheme {
                Scaffold { padding ->
                    Placeholder(Modifier.padding(padding).fillMaxSize())
                }
            }
        }
    }
}

@Composable
private fun Placeholder(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Whirl", style = MaterialTheme.typography.displayMedium)
        Text("v${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium)
    }
}

@Preview
@Composable
private fun PlaceholderPreview() {
    WhirlTheme { Placeholder() }
}
