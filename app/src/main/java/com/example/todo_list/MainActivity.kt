package com.example.todo_list

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.VerticalAlignmentLine
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.todo_list.ui.theme.TodolistTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TodolistTheme {
                Scaffold(modifier = Modifier.fillMaxSize()
                     )
                { innerPadding ->
                    Greeting(
                        name = "Android",
                        modifier = Modifier.padding(innerPadding)
                    )

                }
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {

   Box(contentAlignment = Alignment.Center){
       Text(
           text = "Bienvenue à notre application"
       )
   }
}

@Composable
fun GreetingWithImage(modifier: Modifier = Modifier) {
    val image = painterResource(R.drawable.unamed)
    Box(
        modifier = modifier.fillMaxSize()
            .background(Color(red = 127 , green = 232 , blue = 244,alpha = 171)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.offset(y = (-50).dp)
        ) {
            Image(
                painter = image,
                contentDescription = null,
                modifier = Modifier
                    .height(278.dp)
                    .width(371.dp)
            )
            Text(
                text = "Bienvenue à notre application",
                modifier = Modifier.padding(top = 16.dp, start = 45.dp, end = 25.dp)
                    .fillMaxWidth()
                    .align(Alignment.CenterHorizontally),
                fontWeight = FontWeight.Bold,
                fontSize = 25.sp,
                textAlign = TextAlign.Center


            )
            Text(
                text = "Organisez vos tâches quotidiennes\n" +
                        "efficacement et restez productif.\n" +
                        "Ajoutez, modifiez et suivez vos\n" +
                        "progrès facilement."
                ,
                modifier.padding(start=35.dp,top = 20.dp, end = 35.dp),
                textAlign = TextAlign.Center
            )

        }

    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            ,
        contentAlignment = Alignment.BottomCenter
    ) {
        Button(
            onClick = { /* Action when clicked */ },
            modifier = Modifier
                .padding(bottom = 16.dp)
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF7C5BD8),
                            Color(0xFFF4C27F)
                        )
                    ),
                    shape = RoundedCornerShape(50)
                ),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent
            )
        ) {
            Text(
                text = "Commencer"
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Commencer",
                modifier = Modifier.size(24.dp),
                tint = Color.White
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    TodolistTheme {
        GreetingWithImage()
    }
}

