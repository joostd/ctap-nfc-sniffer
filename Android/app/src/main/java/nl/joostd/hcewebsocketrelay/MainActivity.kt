package nl.joostd.hcewebsocketrelay

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import nl.joostd.hcewebsocketrelay.ui.theme.HostBasedCardEmulationTheme


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HostBasedCardEmulationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ShowMessage("Tap phone to an NFC reader to start relaying APDUs.")
                }
            }
        }
    }
}

@Composable
fun ShowMessage(msg: String, modifier: Modifier = Modifier) {
    Text(
        text = msg,
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun ShowMessagePreview() {
    HostBasedCardEmulationTheme {
        ShowMessage("Host Based Card Emulation")
    }
}
