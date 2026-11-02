package com.asphalt.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asphalt.app.R
import com.asphalt.app.model.DriverProfile
import com.asphalt.app.ui.components.IosCard
import com.asphalt.app.ui.components.IosPrimaryButton
import com.asphalt.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    initialProfile: DriverProfile,
    onComplete: (DriverProfile) -> Unit
) {
    var step by remember { mutableStateOf(1) } // 1 = Tour, 2 = Profile Form

    var firstName by remember { mutableStateOf(initialProfile.firstName) }
    var lastName by remember { mutableStateOf(initialProfile.lastName) }
    var phone by remember { mutableStateOf(initialProfile.phoneWithCountryCode) }
    var governorate by remember { mutableStateOf(initialProfile.governorate) }
    var country by remember { mutableStateOf(initialProfile.country) }
    var currency by remember { mutableStateOf(initialProfile.currency) }

    val governorates = listOf("القاهرة", "الجيزة", "الإسكندرية", "القليوبية", "الشرقية", "الدقهلية", "الغربية", "المنوفية", "البحيرة", "الفيوم", "بني سويف", "الرياض", "جدة", "دبي")

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (step == 1) {
                // Tour Screen
                Spacer(modifier = Modifier.height(10.dp))

                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .background(AppPrimaryTint),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.asphalt_brand_art),
                        contentDescription = "شعار أسفلت V5",
                        modifier = Modifier.size(80.dp)
                    )
                }

                Text(
                    text = "أسفلت V5",
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Text(
                    text = "رفيقك الذكي على الطريق لحساب الأجرة وصافي الربح في ثانية",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                IosCard {
                    TourFeatureRow(
                        icon = Icons.Default.Visibility,
                        iconColor = AppPrimary,
                        title = "قراءة كارت الطلب لحظياً",
                        description = "يقرا الأرقام من شاشة أوبر، ديدي، وإن درايف فقط دون أي تطبيق آخر."
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                    TourFeatureRow(
                        icon = Icons.Default.AccountBalanceWallet,
                        iconColor = VerdictAccept,
                        title = "حكم فوري وصافي في جيبك",
                        description = "يخصم عمولة التطبيق، استهلاك البنزين حسب موتورك، وصيانة الكيلو."
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                    TourFeatureRow(
                        icon = Icons.Default.TouchApp,
                        iconColor = VerdictConsider,
                        title = "للقراءة والحكم فقط",
                        description = "مبيدوسش على أي زرار ومبيقبلش ولا بيرفض ولا بيفاوض بدالك. القرار قرارك أنت دايماً."
                    )
                }

                Spacer(modifier = Modifier.weight(1f, fill = false))

                IosPrimaryButton(
                    text = "التالي: عرّفنا بيك",
                    onClick = { step = 2 },
                    testTag = "onboarding_next_button"
                )
            } else {
                // Profile Setup Screen ("عرّفنا بيك")
                Text(
                    text = "عرّفنا بيك يا كابتن",
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Text(
                    text = "بياناتك بتفضل محفوظة على جهازك فقط لضبط الحسابات والعملة",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary
                )

                IosCard {
                    OutlinedTextField(
                        value = firstName,
                        onValueChange = { firstName = it },
                        label = { Text("الاسم الأول") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("first_name_input"),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = lastName,
                        onValueChange = { lastName = it },
                        label = { Text("اسم العائلة") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("last_name_input"),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("واتساب بكود الدولة") },
                        placeholder = { Text("+201012345678") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("phone_input"),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "المحافظة الأساسية للعمل:",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    var expandedGov by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = expandedGov,
                        onExpandedChange = { expandedGov = !expandedGov }
                    ) {
                        OutlinedTextField(
                            value = governorate,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedGov) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(),
                            shape = RoundedCornerShape(12.dp)
                        )
                        ExposedDropdownMenu(
                            expanded = expandedGov,
                            onDismissRequest = { expandedGov = false }
                        ) {
                            governorates.forEach { gov ->
                                DropdownMenuItem(
                                    text = { Text(gov) },
                                    onClick = {
                                        governorate = gov
                                        expandedGov = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = country,
                            onValueChange = { country = it },
                            label = { Text("البلد") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = currency,
                            onValueChange = { currency = it },
                            label = { Text("العملة") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )
                    }
                }

                IosPrimaryButton(
                    text = "حفظ ومتابعة إعداد القارئ",
                    onClick = {
                        val prof = DriverProfile(
                            firstName = firstName.ifBlank { "كابتن" },
                            lastName = lastName.ifBlank { "محمد" },
                            phoneWithCountryCode = phone.ifBlank { "+201012345678" },
                            governorate = governorate,
                            country = country.ifBlank { "مصر" },
                            currency = currency.ifBlank { "ج.م" }
                        )
                        onComplete(prof)
                    },
                    testTag = "save_profile_button"
                )
            }
        }
    }
}

@Composable
private fun TourFeatureRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(iconColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(24.dp)
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.secondary,
                lineHeight = 18.sp
            )
        }
    }
}
