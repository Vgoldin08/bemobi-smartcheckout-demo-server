package com.example.a

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.clickable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.filled.Check
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import androidx.compose.runtime.rememberCoroutineScope
import com.bemobi.smartcheckout.sdk.android.payment.PaymentConfiguration
import com.bemobi.smartcheckout.sdk.android.payment.PaymentSheet
import com.bemobi.smartcheckout.sdk.android.payment.PaymentSheetResult
import android.util.Log
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import kotlinx.coroutines.delay
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONArray
import android.view.WindowManager

// Classes de dados para a API
data class BillInfo(
    val billId: String,
    val amount: String,
    val dueDate: String,
    val referenceMonth: String = "2025/07",
    val description: String = "2024 - E.F 5º ANO"
)

// Classe para gerenciar as chamadas à API
class PaymentRepository {
    private val demoServerUrl = "https://evqbwjp247.us-east-1.awsapprunner.com"

    suspend fun requestPayment(billInfo: BillInfo): String = withContext(Dispatchers.IO) {
        Log.d("Payment", "Iniciando processo de pagamento...")
        
        val url = URL("$demoServerUrl/createPaymentIntent")
        val connection = url.openConnection() as HttpURLConnection
        
        try {
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true

            // Converter valor para centavos e remover vírgula
            val amountInCents = (billInfo.amount.replace(",", ".")
                .toDouble() * 100).toInt()

            val payload = JSONObject().apply {
                put("price", amountInCents)
            }

            Log.d("Payment", "Enviando payload: ${payload.toString(2)}")
            
            connection.outputStream.use { os ->
                os.write(payload.toString().toByteArray())
            }

            val response = connection.inputStream.bufferedReader().use { it.readText() }
            Log.d("Payment", "Resposta completa do servidor: $response")
            
            val jsonResponse = JSONObject(response)
            val token = jsonResponse.getString("sessionToken")
            Log.d("Payment", "Token extraído: $token")
            
            return@withContext token
        } catch (e: Exception) {
            Log.e("Payment", "Erro ao obter session token", e)
            val errorBody = connection.errorStream?.bufferedReader()?.readText()
            Log.e("Payment", "Corpo do erro: $errorBody")
            throw e
        }
    }
}

private const val LOG_TAG = "MainActivity"
private const val POC_URL = "https://poc.smartcheckout.bemobi.com"

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    private lateinit var paymentSheet: PaymentSheet
    private var currentScreen by mutableStateOf("bills")
    private var selectedAmount by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configurar WebView globalmente
        WebView.setWebContentsDebuggingEnabled(true)  // Habilita debug do WebView
        val webView = WebView(this)
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            setGeolocationEnabled(true)
            allowFileAccess = true
            allowContentAccess = true
            allowFileAccessFromFileURLs = true
            allowUniversalAccessFromFileURLs = true
        }

        // Adicionar flags para garantir que o teclado funcione
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)

        try {
            Log.d(LOG_TAG, "Inicializando SDK...")
            PaymentConfiguration.init(applicationContext)
            paymentSheet = PaymentSheet(this, ::onPaymentSheetResult)
            Log.d(LOG_TAG, "SDK inicializado com sucesso")
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Erro ao inicializar SDK", e)
            Log.e(LOG_TAG, "Stack trace: ${e.stackTraceToString()}")
        }

        enableEdgeToEdge()
        setContent {
            when (currentScreen) {
                "bills" -> BillScreen(
                    onBillClick = { amount ->
                        selectedAmount = amount
                        currentScreen = "details"
                    }
                )
                "details" -> BillDetailsScreen(
                    amount = selectedAmount,
                    onBackClick = { currentScreen = "bills" },
                    onPaymentClick = { /* Removido pois será chamado após o pagamento */ }
                )
                "paid" -> PaidBillScreen(
                    amount = selectedAmount,
                    onBackClick = { currentScreen = "bills" }
                )
            }
        }
    }

    private fun presentPaymentSheet(sessionCode: String) {
        try {
            Log.d(LOG_TAG, "Iniciando apresentação do PaymentSheet com sessionCode: $sessionCode")
            val configuration = PaymentSheet.Configuration.Builder(sessionCode).build()
            Log.d(LOG_TAG, "Configuração criada com sucesso: $configuration")
            
            paymentSheet.presentWithPaymentIntent(configuration)
            Log.d(LOG_TAG, "PaymentSheet apresentado com sucesso")
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Erro ao apresentar PaymentSheet", e)
            Log.e(LOG_TAG, "Stack trace: ${e.stackTraceToString()}")
        }
    }

    private fun onPaymentSheetResult(paymentSheetResult: PaymentSheetResult) {
        Log.d(LOG_TAG, "Recebido resultado do PaymentSheet: $paymentSheetResult")
        when (paymentSheetResult) {
            is PaymentSheetResult.Completed -> {
                Log.d(LOG_TAG, "Payment completed successfully.")
                currentScreen = "paid"
            }
            is PaymentSheetResult.Canceled -> {
                Log.d(LOG_TAG, "Payment was canceled by the user.")
            }
            is PaymentSheetResult.Failed -> {
                Log.e(LOG_TAG, "Payment failed with error:", paymentSheetResult.error)
                Log.e(LOG_TAG, "Error message: ${paymentSheetResult.error.message}")
                Log.e(LOG_TAG, "Stack trace: ${paymentSheetResult.error.stackTraceToString()}")
            }
        }
    }

    fun startPayment(sessionCode: String) {
        presentPaymentSheet(sessionCode)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillScreen(onBillClick: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        // Top bar with logo
        TopAppBar(
            title = { Text("Light") },
            actions = {
                Icon(
                    painter = rememberVectorPainter(Icons.Default.Person),
                    contentDescription = "Profile",
                    tint = Color(0xFF00BFA5)
                )
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.White
            )
        )

        // "1 conta aberta" header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF00BFA5))
                .padding(16.dp)
        ) {
            Text(
                text = "1 conta aberta",
                color = Color.White,
                fontSize = 16.sp
            )
        }

        // Bills list
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BillItem("Novembro 2024", "1994,97", "PAGA", onClick = {})
            BillItem("Novembro 2024", "1794,99", "PAGA", onClick = {})
            BillItem("Dezembro 2024", "1371,79", "PAGA", onClick = {})
            BillItem("Janeiro 2025", "1529,49", "PENDENTE", onClick = { amount ->
                onBillClick(amount)
            })
        }
    }
}

@Composable
fun BillItem(month: String, amount: String, status: String, onClick: (String) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = { onClick(amount) }),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = month,
                color = Color(0xFF00BFA5),
                fontSize = 14.sp
            )
            Text(
                text = "R$$amount",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = status,
                color = if (status == "PAGA") Color(0xFF00BFA5) else Color(0xFFCD853F),
                fontSize = 14.sp
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillDetailsScreen(
    amount: String,
    onBackClick: () -> Unit,
    onPaymentClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { PaymentRepository() }
    var isLoading by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        TopAppBar(
            title = { Text("Light") },
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = Color(0xFF00BFA5)
                    )
                }
            },
            actions = {
                Icon(
                    painter = rememberVectorPainter(Icons.Default.Person),
                    contentDescription = "Profile",
                    tint = Color(0xFF00BFA5)
                )
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.White
            )
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF00BFA5))
                .padding(16.dp)
        ) {
            Text(
                text = "Janeiro 2025",
                color = Color.White,
                fontSize = 16.sp
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFE0F7F5), RoundedCornerShape(8.dp))
                    .padding(16.dp)
            ) {
                Text(
                    text = "FATURA PENDENTE",
                    fontSize = 16.sp,
                    color = Color(0xFF00BFA5)
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "R$1529,49",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Fechou em 22 Jan • Vence em 31 Jan",
                    color = Color.Gray,
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            ActionButton(
                text = "Ver detalhes",
                icon = Icons.Default.List
            )
            
            ActionButton(
                text = "Receber fatura",
                icon = Icons.Default.Email
            )
            
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = Color(0xFF00BFA5)
                    )
                }
            }

            ActionButton(
                text = "Pagamento",
                icon = null,
                showArrow = false,
                enabled = !isLoading,
                onClick = {
                    scope.launch {
                        try {
                            isLoading = true
                            val sessionToken = withContext(Dispatchers.IO) {
                                repository.requestPayment(
                                    BillInfo(
                                        billId = "123",
                                        amount = amount,
                                        dueDate = "31 Jan"
                                    )
                                )
                            }
                            
                            (context as MainActivity).startPayment(sessionToken)
                        } catch (e: Exception) {
                            Log.e("Payment", "Erro ao processar pagamento", e)
                            Toast.makeText(
                                context,
                                "Erro ao iniciar pagamento: ${e.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        } finally {
                            isLoading = false
                        }
                    }
                }
            )
        }
    }
}

@Composable
fun ActionButton(
    text: String, 
    icon: ImageVector?, 
    showArrow: Boolean = true, 
    enabled: Boolean = true,
    onClick: () -> Unit = {}
) {
    ElevatedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        colors = ButtonDefaults.elevatedButtonColors(
            containerColor = Color(0xFF00BFA5),
            contentColor = Color.Black,
            disabledContainerColor = Color(0xFF00BFA5).copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.Black
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = text,
                color = Color.Black,
                fontSize = 16.sp
            )
            if (showArrow) {
                Spacer(modifier = Modifier.weight(1f))
                Icon(
                    imageVector = Icons.Default.ArrowForward,
                    contentDescription = null,
                    tint = Color.Black
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaidBillScreen(amount: String, onBackClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        TopAppBar(
            title = { Text("Light") },
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = Color(0xFF00BFA5)
                    )
                }
            },
            actions = {
                Icon(
                    painter = rememberVectorPainter(Icons.Default.Person),
                    contentDescription = "Profile",
                    tint = Color(0xFF00BFA5)
                )
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.White
            )
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF00BFA5))
                .padding(16.dp)
        ) {
            Text(
                text = "Dezembro 2024",
                color = Color.White,
                fontSize = 16.sp
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFE0F7F5), RoundedCornerShape(8.dp))
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color(0xFF00BFA5),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Fatura paga",
                        fontSize = 16.sp,
                        color = Color(0xFF00BFA5)
                    )
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "R$$amount",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Fechou em 22 Dez • Venceu em 31 Dez",
                    color = Color.Gray,
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            ActionButton(
                text = "Ver detalhes",
                icon = Icons.Default.List
            )
            
            ActionButton(
                text = "Receber fatura",
                icon = Icons.Default.Email
            )
        }
    }
}