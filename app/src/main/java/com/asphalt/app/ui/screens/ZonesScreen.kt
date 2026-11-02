package com.asphalt.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asphalt.app.model.ZoneItem
import com.asphalt.app.model.ZoneType
import com.asphalt.app.ui.components.*
import com.asphalt.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZonesScreen(
    zones: List<ZoneItem>,
    currentGovernorate: String,
    onAddZone: (name: String, gov: String, type: ZoneType, notes: String) -> Unit,
    onDeleteZone: (ZoneItem) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf(0) } // 0 = Preferred, 1 = Avoided, 2 = All
    var showAddDialog by remember { mutableStateOf(false) }

    var selectedGovFilter by remember { mutableStateOf(currentGovernorate) }
    val governorates = listOf("الكل", "القاهرة", "الجيزة", "الإسكندرية", "القليوبية", "الشرقية")

    val filteredZones = zones.filter { zone ->
        val matchesSearch = zone.name.contains(searchQuery, ignoreCase = true) ||
                zone.notes.contains(searchQuery, ignoreCase = true)
        val matchesGov = if (selectedGovFilter == "الكل") true else zone.governorate == selectedGovFilter
        val matchesType = when (selectedTab) {
            0 -> zone.type == ZoneType.PREFERRED
            1 -> zone.type == ZoneType.AVOIDED
            else -> true
        }
        matchesSearch && matchesGov && matchesType
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = AppPrimary,
                contentColor = AppOnPrimary,
                shape = CircleShape,
                modifier = Modifier.testTag("add_zone_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "إضافة منطقة")
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SectionHeader(
                title = "المناطق المفضلة والمتجنّبة",
                subtitle = "حدّد أماكن الشغل اللي بتحبها، والأماكن الزحمة أو الوعرة اللي ترفضها فوراً."
            )

            // Search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("بحث باسم المنطقة أو الشارع...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("search_zone_input"),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            // Segmented tab: Preferred vs Avoided
            IosSegmentedControl(
                options = listOf("المفضلة (${zones.count { it.type == ZoneType.PREFERRED }})", "المتجنّبة (${zones.count { it.type == ZoneType.AVOIDED }})", "الكل (${zones.size})"),
                selectedIndex = selectedTab,
                onSelectIndex = { selectedTab = it }
            )

            // Governorate filter chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                governorates.take(4).forEach { gov ->
                    FilterChip(
                        selected = selectedGovFilter == gov,
                        onClick = { selectedGovFilter = gov },
                        label = { Text(gov, fontSize = 12.sp) }
                    )
                }
            }

            // Zones List
            if (filteredZones.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Place,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = "لا توجد مناطق مطابقة للبحث",
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredZones, key = { it.id }) { zone ->
                        ZoneItemCard(
                            zone = zone,
                            onDelete = { onDeleteZone(zone) }
                        )
                    }
                }
            }
        }
    }

    // Add Zone Dialog
    if (showAddDialog) {
        AddZoneDialog(
            defaultGov = currentGovernorate,
            onDismiss = { showAddDialog = false },
            onConfirm = { name, gov, type, notes ->
                onAddZone(name, gov, type, notes)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun ZoneItemCard(
    zone: ZoneItem,
    onDelete: () -> Unit
) {
    val isPreferred = zone.type == ZoneType.PREFERRED
    val accentColor = if (isPreferred) VerdictAccept else VerdictReject
    val bgAccent = if (isPreferred) VerdictAcceptBg else VerdictRejectBg

    IosCard(
        borderColor = accentColor.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(bgAccent),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPreferred) Icons.Default.ThumbUp else Icons.Default.Block,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = zone.name,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = bgAccent
                        ) {
                            Text(
                                text = if (isPreferred) "مفضلة" else "متجنّبة",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = accentColor,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Text(
                        text = "محافظة ${zone.governorate} • ${zone.notes.ifBlank { "بدون ملاحظات إضافية" }}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.secondary,
                        maxLines = 1
                    )
                }
            }

            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "حذف المنطقة",
                    tint = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}

@Composable
private fun AddZoneDialog(
    defaultGov: String,
    onDismiss: () -> Unit,
    onConfirm: (name: String, gov: String, type: ZoneType, notes: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var gov by remember { mutableStateOf(defaultGov) }
    var type by remember { mutableStateOf(ZoneType.PREFERRED) }
    var notes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إضافة منطقة جديدة") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("اسم المنطقة أو الشارع (مثل: التجمع الخامس، السيدة زينب)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                IosSegmentedControl(
                    options = listOf("مفضلة (حبيبي)", "متجنّبة (ارفضها)"),
                    selectedIndex = if (type == ZoneType.PREFERRED) 0 else 1,
                    onSelectIndex = { type = if (it == 0) ZoneType.PREFERRED else ZoneType.AVOIDED }
                )

                OutlinedTextField(
                    value = gov,
                    onValueChange = { gov = it },
                    label = { Text("المحافظة") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("سبب التفضيل أو التجنب (اختياري)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onConfirm(name, gov, type, notes)
                    }
                },
                enabled = name.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = AppPrimary)
            ) {
                Text("إضافة")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء")
            }
        }
    )
}
