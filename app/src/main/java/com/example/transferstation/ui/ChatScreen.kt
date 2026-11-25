package com.example.transferstation.ui

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.transferstation.R
import com.example.transferstation.domain.model.ChatMessage
import com.example.transferstation.domain.model.ChatSession
import com.example.transferstation.domain.model.GenerationState
import com.example.transferstation.domain.model.GenerationTaskInfo
import com.example.transferstation.domain.model.MessageRole
import com.example.transferstation.domain.model.ModelInfo
import com.example.transferstation.domain.model.ModelProvider
import com.example.transferstation.domain.model.ModelProviderCatalog
import com.example.transferstation.domain.model.PendingAttachment
import com.example.transferstation.domain.model.ProviderConnection
import com.example.transferstation.domain.model.ProviderConnectionInfo
import com.example.transferstation.domain.model.ProviderKind
import com.example.transferstation.presentation.ChatUiState
import com.example.transferstation.presentation.ChatViewModel
import com.example.transferstation.ui.theme.TransferStationTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/** 连接历史抽屉、聊天列表、服务商设置及 Android 图片选择器，并维护页面交互状态。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    initialSessionId: String? = null,
    modifier: Modifier = Modifier,
    viewModel: ChatViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snapshot = state.snapshot
    val connection = snapshot.connections.firstOrNull { it.id == state.selectedProviderId }
    val selectedModel = connection?.let { current ->
        viewModel.modelFor(current, state.selectedModelId ?: current.models.firstOrNull()?.id.orEmpty()).takeIf { it.id.isNotBlank() }
    }
    val context = LocalContext.current
    val application = context.applicationContext as Application
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var showSettings by remember { mutableStateOf(false) }
    var showModelPicker by remember { mutableStateOf(false) }
    var showAddProvider by remember { mutableStateOf(false) }
    var showTransferConsent by remember { mutableStateOf<String?>(null) }
    var sendConfirmation by remember { mutableStateOf<Triple<String, String, Boolean>?>(null) }
    var unsupportedImageDraft by remember { mutableStateOf(false) }
    var imageSourceSheet by remember { mutableStateOf(false) }
    var editingProviderId by remember { mutableStateOf<String?>(null) }
    var connectionToDelete by remember { mutableStateOf<ProviderConnectionInfo?>(null) }
    var hasRequestedNotificationPermission by rememberSaveable { mutableStateOf(false) }
    var notificationPermissionNeedsSettings by remember { mutableStateOf(false) }
    var cameraPermissionNeedsSettings by remember { mutableStateOf(false) }
    var composerText by rememberSaveable(state.openSessionId) { mutableStateOf("") }
    val pendingAttachments = state.pendingAttachments
    val providersToAdd = remember { mutableStateListOf<PendingAttachment>() }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) importPhoto(viewModel, uri) { attachment ->
            providersToAdd.add(attachment)
            viewModel.setPendingAttachments(providersToAdd.toList())
        }
    }
    var cameraOutputUri by remember { mutableStateOf<Uri?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { captured ->
        val uri = cameraOutputUri
        if (captured && uri != null) importPhoto(viewModel, uri) { attachment ->
            providersToAdd.add(attachment)
            viewModel.setPendingAttachments(providersToAdd.toList())
        }
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) openCamera(context) { uri -> cameraOutputUri = uri; camera.launch(uri) }
        else cameraPermissionNeedsSettings = true
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasRequestedNotificationPermission = true
        notificationPermissionNeedsSettings = !granted
    }
    LaunchedEffect(state.openSessionId) {
        providersToAdd.clear()
    }
    LaunchedEffect(initialSessionId) { initialSessionId?.let(viewModel::openSession) }
    LaunchedEffect(drawerState) {
        snapshotFlow { drawerState.targetValue }
            .collect { targetValue ->
                if (targetValue == DrawerValue.Open) {
                    focusManager.clearFocus()
                    keyboardController?.hide()
                }
            }
    }
    LaunchedEffect(snapshot.sessions.size, snapshot.preferences.selectedProviderId) {
        if (snapshot.connections.isNotEmpty() && snapshot.preferences.selectedProviderId == null && state.selectedProviderId == null) {
            val firstConnection = snapshot.connections.first()
            val firstModel = firstConnection.models.firstOrNull()?.id
            if (firstModel != null) viewModel.selectModel(firstConnection.id, firstModel)
        }
    }
    val currentSession = snapshot.sessions.firstOrNull { it.id == state.openSessionId }
    val tasksById = remember(snapshot.tasks) { snapshot.tasks.associateBy(GenerationTaskInfo::id) }
    val latestTaskStates = remember(snapshot.tasks) {
        snapshot.tasks.associate { it.sessionId to it.state }
    }
    val runningCount = snapshot.tasks.count {
        it.state == GenerationState.CONNECTING || it.state == GenerationState.STREAMING || it.state == GenerationState.QUEUED
    }

    ModalNavigationDrawer(
        modifier = modifier.fillMaxSize(),
        drawerState = drawerState,
        drawerContent = {
            SessionDrawer(
                drawerState = drawerState,
                sessions = snapshot.sessions,
                latestTaskStates = latestTaskStates,
                currentSessionId = currentSession?.id,
                onSessionSelected = { session -> scope.launch { drawerState.close() }; viewModel.openSession(session.id) },
                onNewConversation = { scope.launch { drawerState.close() }; viewModel.createNewSession() },
                onRename = viewModel::rename,
                onDelete = viewModel::deleteSession,
                onSettings = { scope.launch { drawerState.close() }; showSettings = true },
            )
        },
    ) {
        Scaffold(
            contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
            topBar = {
                ChatTopBar(
                    title = currentSession?.title ?: stringResource(R.string.app_name),
                    runningCount = runningCount,
                    drawerState = drawerState,
                    onCloseDrawer = { scope.launch { drawerState.close() } },
                    onMenu = {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                        scope.launch { drawerState.open() }
                    },
                    onNewChat = viewModel::createNewSession,
                )
            },
            bottomBar = {
                ChatComposer(
                    text = composerText,
                    attachments = pendingAttachments,
                    selectedProvider = connection,
                    selectedModel = selectedModel,
                    reasoningLevel = state.reasoningLevel,
                    canSend = connection != null && selectedModel != null && !state.isBusy && (composerText.isNotBlank() || pendingAttachments.isNotEmpty()),
                    onTextChanged = { composerText = it },
                    onAttach = { imageSourceSheet = true },
                    onReasoning = viewModel::selectReasoningLevel,
                    onRemoveAttachment = { index ->
                        providersToAdd.removeAt(index).also(viewModel::discardAttachment)
                        viewModel.setPendingAttachments(providersToAdd.toList())
                    },
                    onSend = {
                        val sessionId = state.openSessionId ?: return@ChatComposer
                        val providerId = connection?.id ?: return@ChatComposer
                        val chosenModel = selectedModel ?: return@ChatComposer
                        if (pendingAttachments.isNotEmpty() && !chosenModel.supportsImages) {
                            unsupportedImageDraft = true
                            return@ChatComposer
                        }
                        val missingConsent = providerId !in snapshot.preferences.confirmedProviderIds
                        val historyHasImages = state.messages.any { it.imagePaths.isNotEmpty() }
                        if (missingConsent) showTransferConsent = providerId
                        else if (!chosenModel.supportsImages && historyHasImages) sendConfirmation = Triple(sessionId, composerText, true)
                        else sendConfirmation = Triple(sessionId, composerText, false)
                    },
                    onModel = { showModelPicker = true },
                )
            },
            containerColor = MaterialTheme.colorScheme.background,
            modifier = Modifier,
        ) { innerPadding ->
            Column(Modifier.fillMaxSize().padding(innerPadding)) {
                if (connection == null) {
                    SetupWelcomeCard(
                        configuredProviders = snapshot.connections.size,
                        onSetup = { showSettings = true },
                    )
                } else if (selectedModel == null) {
                    SelectFirstModelCard(onChooseModel = { showModelPicker = true }, onAddModel = { showSettings = true })
                }
                state.errorMessage?.let { error ->
                    FeedbackCard(text = error, isError = true, onDismiss = viewModel::dismissMessages)
                }
                state.infoMessage?.let { info ->
                    FeedbackCard(text = info, isError = false, onDismiss = viewModel::dismissMessages)
                }
                when {
                    state.isLoadingMessages -> LoadingMessages()
                    state.messages.isEmpty() -> WelcomeGreeting(
                        hasProvider = connection != null,
                        modelName = selectedModel?.name,
                    )
                    else -> key(state.openSessionId) {
                        ConversationMessages(
                            messages = state.messages,
                            tasksById = tasksById,
                            modifier = Modifier.weight(1f),
                            onRetry = viewModel::retry,
                            onStop = viewModel::stop,
                        )
                    }
                }
            }
        }
    }

    if (showModelPicker) {
        ModelPickerSheet(
            connections = snapshot.connections,
            currentProviderId = state.selectedProviderId,
            currentModelId = state.selectedModelId,
            reasoningLevel = state.reasoningLevel,
            onSelect = { providerId, model -> viewModel.selectModel(providerId, model.id); showModelPicker = false },
            onReasoningSelected = viewModel::selectReasoningLevel,
            onRefresh = { providerId, onComplete -> viewModel.refreshModels(providerId, onComplete) },
            onManageProviders = { showModelPicker = false; showSettings = true },
            onDismiss = { showModelPicker = false },
        )
    }

    if (showSettings) {
        ProviderSettingsSheet(
            state = state,
            onAddConnection = { showAddProvider = true },
            onEditConnection = { id -> showAddProvider = true; editingProviderId = id },
            onDeleteConnection = { connectionInfo -> connectionToDelete = connectionInfo },
            onRefreshModels = { id, onComplete -> viewModel.refreshModels(id, onComplete) },
            onThemeSelected = viewModel::setTheme,
            onClose = { showSettings = false },
        )
    }

    if (showAddProvider) {
        ProviderEditorDialog(
            existing = editingProviderId?.let { id -> state.snapshot.connections.firstOrNull { it.id == id } },
            onDismiss = { showAddProvider = false; editingProviderId = null },
            onLoadSecret = { complete -> editingProviderId?.let { id -> viewModel.loadConnectionSecret(id) { saved -> complete(saved?.apiKey.orEmpty()) } } },
            onSave = { kind, name, url, key, id, images, levels ->
                viewModel.saveConnection(kind, name, url, key, id, images, levels, refreshModels = false) { result ->
                    result.onSuccess { savedId -> viewModel.refreshModels(savedId) { }; showAddProvider = false; editingProviderId = null }
                }
            },
        )
    }

    connectionToDelete?.let { deleting ->
        AlertDialog(
            onDismissRequest = { connectionToDelete = null },
            title = { Text("删除 API 配置？") },
            text = { Text("删除“${deleting.connectionDisplayName()}”的密钥配置不会删除已保存的历史会话。") },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteConnection(deleting.id); connectionToDelete = null }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { connectionToDelete = null }) { Text("取消") } },
        )
    }

    if (unsupportedImageDraft) {
        AlertDialog(
            onDismissRequest = { unsupportedImageDraft = false },
            title = { Text("当前模型不支持待发送图片") },
            text = { Text("移除输入框中的图片，或切换到支持识图的模型后再发送。图片尚未传输到服务商。") },
            confirmButton = {
                TextButton(onClick = {
                    state.pendingAttachments.forEach(viewModel::discardAttachment)
                    providersToAdd.clear()
                    viewModel.setPendingAttachments(emptyList())
                    unsupportedImageDraft = false
                }) { Text("移除图片") }
            },
            dismissButton = {
                TextButton(onClick = {
                    unsupportedImageDraft = false
                    showModelPicker = true
                }) { Text("切换模型") }
            },
        )
    }

    val activeSendConfirmation = sendConfirmation
    if (activeSendConfirmation != null) {
        val activeConnection = snapshot.connections.firstOrNull { it.id == state.selectedProviderId }
        val pendingOnlySendText = activeSendConfirmation.third
        AlertDialog(
            onDismissRequest = { sendConfirmation = null },
            title = { Text(if (pendingOnlySendText) "跳过历史图片？" else "发送消息？") },
            text = { Text(if (pendingOnlySendText) "当前模型只支持文字输入。本次请求会排除本会话中的图片，聊天记录中的原图仍可查看。" else "本次对话及附件会发送至 ${activeConnection?.connectionDisplayName() ?: "当前服务商"}，用于生成回答。") },
            confirmButton = {
                TextButton(onClick = {
                    sendConfirmation = null
                    if (activeConnection?.id !in snapshot.preferences.confirmedProviderIds) activeConnection?.id?.let(viewModel::confirmProviderTransfer)
                    viewModel.send(activeSendConfirmation.second, activeSendConfirmation.first, pendingOnlySendText)
                    requestNotificationPermissionIfNeeded(context, notificationPermission, hasRequestedNotificationPermission)
                    composerText = ""
                    providersToAdd.clear()
                }) { Text("继续发送") }
            },
            dismissButton = { TextButton(onClick = { sendConfirmation = null }) { Text("取消") } },
        )
    }

    val consentProviderId = showTransferConsent
    if (consentProviderId != null) {
        val consentProvider = snapshot.connections.firstOrNull { it.id == consentProviderId }
        AlertDialog(
            onDismissRequest = { showTransferConsent = null },
            title = { Text("发送内容至 ${consentProvider?.connectionDisplayName() ?: "服务商"}？") },
            text = { Text("本应用在你的手机上保存会话和 API 配置。发送后，你的消息和所选图片会直接传输至该模型服务商处理。${consentProvider?.baseUrl?.takeIf { consentProvider.kind == ProviderKind.CUSTOM }?.let { "\n\n接口地址：$it" }.orEmpty()}") },
            confirmButton = {
                TextButton(onClick = {
                    showTransferConsent = null
                    viewModel.confirmProviderTransfer(consentProviderId)
                    val selectedModel = selectedModel ?: return@TextButton
                    val sessionId = state.openSessionId ?: return@TextButton
                    val omitImages = !selectedModel.supportsImages && state.messages.any { it.imagePaths.isNotEmpty() }
                    if (omitImages) sendConfirmation = Triple(sessionId, composerText, true)
                    else {
                        viewModel.send(composerText, sessionId, false)
                        requestNotificationPermissionIfNeeded(context, notificationPermission, hasRequestedNotificationPermission)
                        composerText = ""
                        providersToAdd.clear()
                    }
                }) { Text("了解并继续") }
            },
            dismissButton = { TextButton(onClick = { showTransferConsent = null }) { Text("取消") } },
        )
    }

    if (imageSourceSheet) {
        ImageSourceSheet(
            canAddImage = selectedModel?.supportsImages == true,
            onGallery = { imageSourceSheet = false; photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            onCamera = {
                imageSourceSheet = false
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    openCamera(context) { uri -> cameraOutputUri = uri; camera.launch(uri) }
                } else cameraPermission.launch(Manifest.permission.CAMERA)
            },
            onDismiss = { imageSourceSheet = false },
        )
    }

    if (cameraPermissionNeedsSettings) {
        AlertDialog(
            onDismissRequest = { cameraPermissionNeedsSettings = false },
            title = { Text("无法使用相机") },
            text = { Text("请在 Android 应用权限中允许相机访问，再试一次。你仍可从相册选择图片，或继续文字对话。") },
            confirmButton = { TextButton(onClick = { cameraPermissionNeedsSettings = false; openAppSettings(context) }) { Text("打开系统设置") } },
            dismissButton = { TextButton(onClick = { cameraPermissionNeedsSettings = false }) { Text("暂不设置") } },
        )
    }

    if (notificationPermissionNeedsSettings) {
        AlertDialog(
            onDismissRequest = { notificationPermissionNeedsSettings = false },
            title = { Text("允许后台完成提醒") },
            text = { Text("模型任务仍会继续运行并在应用内保存状态。允许通知可在后台任务完成时提醒你。") },
            confirmButton = { TextButton(onClick = { notificationPermissionNeedsSettings = false; openAppNotificationSettings(context) }) { Text("管理通知") } },
            dismissButton = { TextButton(onClick = { notificationPermissionNeedsSettings = false }) { Text("知道了") } },
        )
    }
}


/**
 * 展示聊天标题和核心导航按钮，并使用平台状态栏和导航栏内边距。
 *
 * @param title 当前对话标题。
 * @param runningCount 正在运行的任务数。
 * @param drawerState 当前历史对话抽屉状态。
 * @param onCloseDrawer 关闭历史对话抽屉。
 * @param onMenu 打开历史对话抽屉。
 * @param onNewChat 创建新对话。
 */
@Composable
private fun ChatTopBar(
    title: String,
    runningCount: Int,
    drawerState: DrawerState,
    onCloseDrawer: () -> Unit,
    onMenu: () -> Unit,
    onNewChat: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 7.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            CircleIconButton("打开历史对话", "☰", onMenu)
            Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                Text("TRANSFER STATION", fontSize = 9.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp)
                Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
            if (runningCount > 0) StatusPill("◌ $runningCount", "有任务运行中", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
            CircleIconButton("新建对话", "+", onNewChat)
        }
    }
    BackHandler(enabled = !drawerState.isClosed) { onCloseDrawer() }
}

/** 在状态栏下方显示本机历史、会话操作和账户设置入口。 */
@Composable
private fun SessionDrawer(
    drawerState: DrawerState,
    sessions: List<ChatSession>,
    latestTaskStates: Map<String, GenerationState>,
    currentSessionId: String?,
    onSessionSelected: (ChatSession) -> Unit,
    onNewConversation: () -> Unit,
    onRename: (ChatSession, String) -> Unit,
    onDelete: (String) -> Unit,
    onSettings: () -> Unit,
) {
    var renameSession by remember { mutableStateOf<ChatSession?>(null) }
    var deleteSession by remember { mutableStateOf<ChatSession?>(null) }
    ModalDrawerSheet(
        drawerState = drawerState,
        modifier = Modifier.fillMaxWidth(0.87f),
        drawerContainerColor = MaterialTheme.colorScheme.surface,
        drawerShape = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp),
        windowInsets = WindowInsets.statusBars,
    ) {
        Column(Modifier.fillMaxSize().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(Modifier.fillMaxWidth().padding(bottom = 20.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("TRANSFER", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    Text("模型对话", fontSize = 21.sp, fontWeight = FontWeight.Bold)
                }
                CircleIconButton("新建对话", "+", onNewConversation)
            }
            Button(onClick = onNewConversation, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                Text("＋  新对话")
            }
            Text("最近对话", modifier = Modifier.padding(top = 24.dp, bottom = 8.dp), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(sessions, key = ChatSession::id) { session ->
                    val sessionTaskState = latestTaskStates[session.id]
                    var menuExpanded by remember(session.id) { mutableStateOf(false) }
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp))
                            .background(
                                if (session.id == currentSessionId) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.52f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.36f)
                                },
                            )
                            .clickable { onSessionSelected(session) }
                            .padding(start = 13.dp, end = 3.dp, top = 10.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(session.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = if (session.id == currentSessionId) FontWeight.SemiBold else FontWeight.Normal, fontSize = 14.sp)
                            Text(sessionTaskState?.displayLabel() ?: session.updatedAt.displayTimestamp(), fontSize = 11.sp, color = if (sessionTaskState in setOf(GenerationState.FAILED, GenerationState.INTERRUPTED)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Box {
                            IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(38.dp).semantics { contentDescription = "管理会话" }) { Text("⋯", fontSize = 21.sp) }
                            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                                DropdownMenuItem(text = { Text("重命名") }, onClick = { menuExpanded = false; renameSession = session })
                                DropdownMenuItem(text = { Text("删除会话") }, onClick = { menuExpanded = false; deleteSession = session })
                            }
                        }
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.42f))
            NavigationDrawerItem(
                selected = false,
                onClick = onSettings,
                icon = { Text("⚙", fontSize = 18.sp) },
                label = { Text("API 与外观设置") },
                modifier = Modifier.padding(top = 9.dp),
                colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = Color.Transparent),
            )
            Text("密钥与对话只保存在本机", Modifier.align(Alignment.CenterHorizontally).padding(top = 10.dp, bottom = 4.dp), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    renameSession?.let { session ->
        RenameSessionDialog(session = session, onDismiss = { renameSession = null }, onRename = { title -> onRename(session, title); renameSession = null })
    }
    deleteSession?.let { session ->
        AlertDialog(
            onDismissRequest = { deleteSession = null },
            title = { Text("删除会话？") },
            text = { Text("“${session.title}”及其消息和本地图片会被删除。") },
            confirmButton = { TextButton(onClick = { onDelete(session.id); deleteSession = null }) { Text("删除") } },
            dismissButton = { TextButton(onClick = { deleteSession = null }) { Text("取消") } },
        )
    }
}

/** 渲染顶部服务商和模型胶囊、思考选项及独立附件操作。 */
@Composable
private fun ChatComposer(
    text: String,
    attachments: List<PendingAttachment>,
    selectedProvider: ProviderConnectionInfo?,
    selectedModel: ModelInfo?,
    reasoningLevel: String?,
    canSend: Boolean,
    onTextChanged: (String) -> Unit,
    onAttach: () -> Unit,
    onReasoning: (String?) -> Unit,
    onRemoveAttachment: (Int) -> Unit,
    onSend: () -> Unit,
    onModel: () -> Unit,
) {
    var expandedReasoning by remember { mutableStateOf(false) }
    val messagePlaceholder = selectedModel?.let { model ->
        val providerLabel = ModelProviderCatalog.identify(selectedProvider?.kind, model.id, model.name)?.brand
            ?: selectedProvider?.kind?.displayName
            ?: "模型"
        stringResource(R.string.chat_message_placeholder_with_model, providerLabel)
    } ?: stringResource(R.string.chat_message_placeholder)
    Surface(
        color = MaterialTheme.colorScheme.background,
        tonalElevation = 3.dp,
        modifier = Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 13.dp, vertical = 7.dp),
        shape = RoundedCornerShape(25.dp),
        shadowElevation = 5.dp,
        border = androidx.compose.foundation.BorderStroke(0.8.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.57f)),
    ) {
        Column(Modifier.padding(horizontal = 13.dp, vertical = 11.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            if (attachments.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    attachments.forEachIndexed { index, attachment ->
                        Box {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current).data(File(attachment.localPath)).crossfade(true).build(),
                                contentDescription = "待发送图片 ${index + 1}",
                                modifier = Modifier.size(69.dp).clip(RoundedCornerShape(15.dp)),
                            )
                            Surface(
                                modifier = Modifier.align(Alignment.TopEnd).padding(3.dp).size(21.dp).clickable { onRemoveAttachment(index) },
                                color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.72f),
                                shape = CircleShape,
                            ) { Box(contentAlignment = Alignment.Center) { Text("×", color = Color.White, fontSize = 14.sp) } }
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                CircleIconButton("添加图片", "+", onAttach, size = 34.dp)
                Box(Modifier.weight(1f)) {
                    if (text.isEmpty()) Text(messagePlaceholder, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp)
                    BasicTextField(
                        value = text,
                        onValueChange = onTextChanged,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 34.dp, max = 138.dp),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, keyboardType = KeyboardType.Text),
                    )
                }
                FilledIconButton(
                    onClick = onSend,
                    enabled = canSend,
                    modifier = Modifier.size(43.dp).semantics { contentDescription = "发送消息" },
                    shape = CircleShape,
                ) { Text("↑", fontSize = 25.sp, fontWeight = FontWeight.Medium) }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CompactChip(
                    label = selectedModel?.name ?: "选择模型",
                    enabled = selectedModel != null,
                    onClick = onModel,
                    modifier = Modifier.weight(1f, fill = false),
                    providerKind = selectedProvider?.kind,
                    providerLabel = selectedProvider?.name,
                    providerModelId = selectedModel?.id,
                )
                if (selectedModel?.reasoningLevels?.isNotEmpty() == true) {
                    Box {
                        CompactChip(label = reasoningLevel?.let { "$it 思考" } ?: "思考强度", enabled = true, onClick = { expandedReasoning = true })
                        DropdownMenu(expanded = expandedReasoning, onDismissRequest = { expandedReasoning = false }) {
                            DropdownMenuItem(text = { Text("默认") }, onClick = { onReasoning(null); expandedReasoning = false })
                            selectedModel.reasoningLevels.forEach { level -> DropdownMenuItem(text = { Text(level) }, onClick = { onReasoning(level); expandedReasoning = false }) }
                        }
                    }
                }
                Text(
                    if (selectedModel?.supportsImages == true) "识图" else "仅文本",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f).then(Modifier),
                )
            }
        }
    }
}

/**
 * 使用稳定消息键渲染历史消息，并在用户停留底部时节流处理流式滚动。
 *
 * @param messages 当前会话消息。
 * @param tasksById 后台生成任务快照。
 * @param modifier 消息列表布局修饰符。
 * @param onRetry 重试失败生成任务。
 * @param onStop 停止生成任务。
 */
@Composable
private fun ConversationMessages(
    messages: List<ChatMessage>,
    tasksById: Map<String, GenerationTaskInfo>,
    modifier: Modifier = Modifier,
    onRetry: (String) -> Unit,
    onStop: (String) -> Unit,
) {
    val currentMessages by rememberUpdatedState(messages)
    val currentTasks by rememberUpdatedState(tasksById)
    val messagesState = rememberLazyListState(
        initialFirstVisibleItemIndex = messages.lastIndex.coerceAtLeast(0),
    )
    LaunchedEffect(messagesState, messages.size) {
        if (messages.isNotEmpty()) messagesState.scrollToItem(messages.lastIndex)
    }
    LaunchedEffect(messagesState) {
        var lastScrollAtMs = 0L
        snapshotFlow {
            currentMessages.lastOrNull()?.let { message ->
                val generatedText = message.taskId?.let(currentTasks::get)?.assistantText.orEmpty()
                message.id to (message.text.length + generatedText.length)
            }
        }
            .collect {
                val now = android.os.SystemClock.uptimeMillis()
                val elapsed = now - lastScrollAtMs
                if (lastScrollAtMs > 0L && elapsed < STREAM_SCROLL_INTERVAL_MS) {
                    delay(STREAM_SCROLL_INTERVAL_MS - elapsed)
                }
                val layout = messagesState.layoutInfo
                val lastVisibleIndex = layout.visibleItemsInfo.lastOrNull()?.index
                val distanceFromEnd = layout.totalItemsCount - (lastVisibleIndex ?: 0) - 1
                val latestMessages = currentMessages
                if (distanceFromEnd <= 1 && latestMessages.isNotEmpty()) {
                    messagesState.scrollToItem(latestMessages.lastIndex)
                }
                lastScrollAtMs = android.os.SystemClock.uptimeMillis()
            }
    }
    LazyColumn(
        state = messagesState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        items(messages, key = ChatMessage::id) { message ->
            val task = message.taskId?.let(tasksById::get)
            MessageRow(
                message = message,
                taskText = task?.assistantText.orEmpty(),
                taskReasoningSummary = task?.reasoningSummary,
                taskState = task?.state,
                taskError = task?.errorMessage,
                taskStartedAt = task?.startedAt,
                providerKind = task?.providerKind,
                modelProvider = ModelProviderCatalog.identify(task?.providerKind, task?.modelId, message.modelName ?: task?.modelName),
                onRetry = task?.id?.let { taskId -> { onRetry(taskId) } },
                onStop = task?.id?.let { taskId -> { onStop(taskId) } },
            )
        }
    }
}

private const val STREAM_SCROLL_INTERVAL_MS = 48L

/** 显示一条用户或 AI 消息、模型元数据、任务状态和已保存的图片。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageRow(
    message: ChatMessage,
    taskText: String,
    taskReasoningSummary: String?,
    taskState: GenerationState?,
    taskError: String?,
    taskStartedAt: Long?,
    providerKind: ProviderKind?,
    modelProvider: ModelProvider?,
    onRetry: (() -> Unit)?,
    onStop: (() -> Unit)?,
) {
    val isUser = message.role == MessageRole.USER
    val displayText = message.text.ifEmpty { taskText }
    val displayReasoningSummary = message.reasoningSummary ?: taskReasoningSummary
    var elapsedNow by remember(taskStartedAt) { mutableLongStateOf(System.currentTimeMillis()) }
    val currentLocale = LocalConfiguration.current.locales[0]
    LaunchedEffect(taskStartedAt, taskState) {
        while (
            taskStartedAt != null &&
            taskState in setOf(GenerationState.QUEUED, GenerationState.CONNECTING, GenerationState.STREAMING)
        ) {
            delay(1_000)
            elapsedNow = System.currentTimeMillis()
        }
    }
    var expandedSummary by remember(message.id) { mutableStateOf(false) }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val availableWidth = maxWidth
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
            verticalAlignment = Alignment.Top,
        ) {
            if (!isUser) ProviderLogo(providerKind, modelProvider?.brand ?: message.providerName.orEmpty(), size = 29.dp)
            Column(
                modifier = Modifier.widthIn(max = if (isUser) availableWidth * 0.86f else availableWidth * 0.94f)
                    .padding(start = if (isUser) 0.dp else 9.dp, end = if (isUser) 0.dp else 28.dp),
                horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                if (message.imagePaths.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    message.imagePaths.forEachIndexed { index, path ->
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current).data(File(path)).crossfade(true).build(),
                            contentDescription = "${if (isUser) "你发送的" else "对话中的"}图片 ${index + 1}",
                            modifier = Modifier.widthIn(max = 208.dp).heightIn(max = 216.dp).clip(RoundedCornerShape(18.dp)),
                        )
                    }
                }
                if (displayText.isNotEmpty() || taskState != null) {
                    if (isUser) {
                        Surface(shape = RoundedCornerShape(topStart = 22.dp, topEnd = 7.dp, bottomEnd = 22.dp, bottomStart = 22.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                            SelectionContainer { Text(displayText, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), fontSize = 15.sp, lineHeight = 24.sp) }
                        }
                    } else {
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                            if (displayText.isNotEmpty()) SelectionContainer {
                                Text(
                                    text = richText(displayText),
                                    fontSize = 15.sp,
                                    lineHeight = 25.sp,
                                    color = MaterialTheme.colorScheme.onBackground,
                                )
                            }
                            if (taskState == GenerationState.STREAMING || taskState == GenerationState.CONNECTING || taskState == GenerationState.QUEUED) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (taskState != GenerationState.QUEUED) CircularProgressIndicator(Modifier.size(15.dp), strokeWidth = 2.dp)
                                    Text(taskState?.displayLabel().orEmpty() + if (taskStartedAt != null) " · ${elapsedLabel(taskStartedAt, elapsedNow)}" else "", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    onStop?.let { TextButton(onClick = it) { Text("停止", fontSize = 12.sp) } }
                                }
                            }
                            if (taskState in setOf(GenerationState.FAILED, GenerationState.INTERRUPTED, GenerationState.STOPPED)) {
                                Surface(shape = RoundedCornerShape(13.dp), color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f)) {
                                    Column(Modifier.padding(11.dp)) {
                                        Text("${taskState?.displayLabel().orEmpty()}${taskError?.let { "：$it" }.orEmpty()}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onErrorContainer)
                                        onRetry?.let { TextButton(onClick = it) { Text("重试") } }
                                    }
                                }
                            }
                            if (!displayReasoningSummary.isNullOrBlank()) {
                                Surface(
                                    modifier = Modifier.fillMaxWidth().clickable { expandedSummary = !expandedSummary },
                                    shape = RoundedCornerShape(17.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f),
                                    border = androidx.compose.foundation.BorderStroke(0.6.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.52f)),
                                ) {
                                    Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                                        Text(if (expandedSummary) "思考摘要　⌃" else "思考摘要　⌄", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
                                        AnimatedVisibility(expandedSummary) { SelectionContainer { Text(displayReasoningSummary.orEmpty(), Modifier.padding(top = 7.dp), fontSize = 12.sp, lineHeight = 19.sp) } }
                                    }
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                ProviderLogo(providerKind, modelProvider?.brand ?: message.providerName.connectionDisplayName() ?: "模型", 13.dp, message.modelName, message.modelName)
                                Text("${modelProvider?.brand ?: message.providerName.connectionDisplayName() ?: "模型"} · ${message.modelName ?: "AI"}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                message.reasoningLevel?.let { Text("· $it", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                message.requestDurationMs?.let { Text("· ${(it / 1000.0).let { time -> String.format(currentLocale, "%.1f 秒", time) }}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 显示可选模型、提供方能力和模型目录刷新入口。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelPickerSheet(
    connections: List<ProviderConnectionInfo>,
    currentProviderId: String?,
    currentModelId: String?,
    reasoningLevel: String?,
    onSelect: (String, ModelInfo) -> Unit,
    onReasoningSelected: (String?) -> Unit,
    onRefresh: (String, (Result<List<ModelInfo>>) -> Unit) -> Unit,
    onManageProviders: () -> Unit,
    onDismiss: () -> Unit,
) {
    var refreshingProviderId by remember { mutableStateOf<String?>(null) }
    var search by remember { mutableStateOf("") }
    var manualModelProvider by remember { mutableStateOf<ProviderConnectionInfo?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 21.dp).padding(bottom = 30.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("选择模型", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("模型能力按服务商目录推断。", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(search, { search = it }, Modifier.fillMaxWidth(), label = { Text("搜索模型") }, singleLine = true)
            LazyColumn(Modifier.heightIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                connections.forEach { connection ->
                    item(key = "header:${connection.id}") {
                        Row(Modifier.fillMaxWidth().padding(top = 7.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ProviderLogo(connection.kind, connection.connectionDisplayName(), 26.dp)
                                Column {
                                    Text(connection.connectionDisplayName(), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                    Text("${connection.models.size} 个模型", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = { manualModelProvider = connection }) { Text("手动输入") }
                                TextButton(onClick = {
                                    refreshingProviderId = connection.id
                                    onRefresh(connection.id) { refreshingProviderId = null }
                                }) {
                                    if (refreshingProviderId == connection.id) CircularProgressIndicator(Modifier.size(15.dp), strokeWidth = 2.dp)
                                    else Text("刷新")
                                }
                            }
                        }
                    }
                    items(connection.models.filter { it.name.contains(search, true) || it.id.contains(search, true) }.take(100), key = { connection.id + ":" + it.id }) { model ->
                        ModelOption(
                            connection = connection,
                            model = model,
                            selected = connection.id == currentProviderId && model.id == currentModelId,
                            reasoningLevel = reasoningLevel,
                            onClick = { onSelect(connection.id, model) },
                            onReasoning = onReasoningSelected,
                        )
                    }
                    if (connection.models.isEmpty()) item(key = "empty:${connection.id}") {
                        Text("暂时没有模型目录。刷新列表，或手动填写已开通的模型 ID。", Modifier.padding(start = 34.dp, top = 5.dp, bottom = 8.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            TextButton(onClick = onManageProviders, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("管理服务商连接") }
        }
    }
    manualModelProvider?.let { connection ->
        ManualModelDialog(connection.connectionDisplayName(), onDismiss = { manualModelProvider = null }, onConfirm = { id ->
            if (id.isNotBlank()) onSelect(connection.id, ModelInfo(id.trim(), id.trim(), connection.customSupportsImages, connection.customReasoningLevels, "手动能力声明"))
            manualModelProvider = null
        })
    }
}

/** 呈现单条模型目录项、选择状态和当前模型可用思考档位。 */
@Composable
private fun ModelOption(
    connection: ProviderConnectionInfo,
    model: ModelInfo,
    selected: Boolean,
    reasoningLevel: String?,
    onClick: () -> Unit,
    onReasoning: (String?) -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp))
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 9.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Text(if (selected) "●" else "○", color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
            Column(Modifier.weight(1f)) {
                Text(model.name, fontSize = 13.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(model.id, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (model.supportsImages || connection.customSupportsImages) StatusPill("图片", "支持图片输入", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
        }
        if (selected && model.reasoningLevels.isNotEmpty()) {
            Row(Modifier.padding(start = 21.dp, top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                model.reasoningLevels.forEach { level ->
                    FilterChip(label = level, selected = reasoningLevel == level, onClick = { onReasoning(if (reasoningLevel == level) null else level) })
                }
            }
        }
    }
}

/** 编辑服务商 API Key、接口地址及自定义模型能力。 */
@Composable
private fun ProviderEditorDialog(
    existing: ProviderConnectionInfo?,
    onDismiss: () -> Unit,
    onLoadSecret: (((String) -> Unit) -> Unit),
    onSave: (ProviderKind, String, String, String, String?, Boolean, List<String>) -> Unit,
) {
    var kindName by remember(existing?.id) { mutableStateOf(existing?.kind?.name ?: ProviderKind.OPENAI.name) }
    val kind = ProviderKind.entries.firstOrNull { it.name == kindName } ?: ProviderKind.OPENAI
    var name by remember(existing?.id) { mutableStateOf(existing?.name ?: kind.displayName) }
    var baseUrl by remember(existing?.id) { mutableStateOf(existing?.baseUrl ?: kind.defaultBaseUrl()) }
    var apiKey by remember(existing?.id) { mutableStateOf(if (existing?.credentialsAvailable == true) "••••••••" else "") }
    var revealApiKey by remember(existing?.id) { mutableStateOf(false) }
    var secretLoaded by remember(existing?.id) { mutableStateOf(existing == null) }
    var customImages by remember(existing?.id) { mutableStateOf(existing?.customSupportsImages ?: false) }
    var customReasoning by remember(existing?.id) { mutableStateOf(existing?.customReasoningLevels?.isNotEmpty() ?: false) }
    var expandedKind by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(existing?.id) {
        if (existing != null && !secretLoaded) onLoadSecret { savedKey ->
            apiKey = savedKey
            secretLoaded = true
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "添加服务商" else "编辑服务商") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 550.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                Box {
                    OutlinedButton(onClick = { if (existing == null) expandedKind = true }, enabled = existing == null, modifier = Modifier.fillMaxWidth()) {
                        ProviderLogo(kind, kind.displayName, 18.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("提供方　${kind.displayName}")
                    }
                    DropdownMenu(expanded = expandedKind, onDismissRequest = { expandedKind = false }) {
                        ProviderKind.entries.forEach { option -> DropdownMenuItem(text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                ProviderLogo(option, option.displayName, 20.dp)
                                Spacer(Modifier.width(10.dp))
                                Text(option.displayName)
                            }
                        }, onClick = {
                            kindName = option.name
                            name = option.displayName
                            baseUrl = option.defaultBaseUrl()
                            expandedKind = false
                        }) }
                    }
                }
                OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("显示名称") }, singleLine = true)
                OutlinedTextField(
                    baseUrl,
                    { baseUrl = it },
                    Modifier.fillMaxWidth(),
                    label = { Text("Base URL") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, capitalization = KeyboardCapitalization.None),
                    supportingText = {
                        Text(
                            if (kind == ProviderKind.CUSTOM) "填写完整的 OpenAI 兼容 API 根地址，例如 https://skyapi2026.com/v1。"
                            else "请求通过手机直连该 HTTPS 地址",
                        )
                    },
                )
                OutlinedTextField(
                    apiKey,
                    { apiKey = it },
                    Modifier.fillMaxWidth(),
                    label = { Text("API Key") },
                    singleLine = true,
                    visualTransformation = if (revealApiKey) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        TextButton(onClick = {
                            if (existing != null && !secretLoaded) onLoadSecret { savedKey -> apiKey = savedKey; secretLoaded = true }
                            revealApiKey = !revealApiKey
                        }) { Text(if (revealApiKey) "隐藏" else "显示", fontSize = 11.sp) }
                    },
                )
                if (kind == ProviderKind.CUSTOM) {
                    HorizontalDivider()
                    Text("兼容接口能力须由用户确认", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    PreferenceToggleRow("支持图片输入", customImages) { customImages = it }
                    PreferenceToggleRow("支持思考强度参数", customReasoning) { customReasoning = it }
                    if (customReasoning) Text("首版按 low、medium、high 参数传递。", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                localError?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (name.isBlank() || apiKey.isBlank() && existing == null || !baseUrl.startsWith("https://", true)) {
                    localError = "请填写名称、API Key 和有效的 HTTPS 地址。"
                } else onSave(
                    kind,
                    name,
                    baseUrl,
                    apiKey,
                    existing?.id,
                    customImages,
                    if (customReasoning) listOf("低", "中", "高") else emptyList(),
                )
            }) { Text("保存并检查模型") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/** 提供商设置面板独立滚动内容，并避免面板拖拽与卡片滚动争抢手势。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProviderSettingsSheet(
    state: ChatUiState,
    onAddConnection: () -> Unit,
    onEditConnection: (String) -> Unit,
    onDeleteConnection: (ProviderConnectionInfo) -> Unit,
    onRefreshModels: (String, (Result<List<ModelInfo>>) -> Unit) -> Unit,
    onThemeSelected: (String) -> Unit,
    onClose: () -> Unit,
) {
    var pendingDelete by remember { mutableStateOf<ProviderConnectionInfo?>(null) }
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = sheetState,
        sheetGesturesEnabled = false,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 27.dp, topEnd = 27.dp),
    ) {
        Column(
            Modifier.fillMaxWidth()
                .heightIn(max = 740.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 19.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(15.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("设置", fontWeight = FontWeight.Bold, fontSize = 23.sp)
                    Text("API Key 与会话保存在本机", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                CircleIconButton("关闭设置", "×", onClose)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("模型服务商", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                TextButton(onClick = onAddConnection) { Text("＋ 添加") }
            }
            if (state.snapshot.connections.isEmpty()) Text("添加 API Key 后即可连接模型。", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            state.snapshot.connections.forEach { connection ->
                ConnectionSettingsCard(
                    connection = connection,
                    isSelected = connection.id == state.selectedProviderId,
                    onEdit = { onEditConnection(connection.id) },
                    onDelete = { pendingDelete = connection },
                    onRefresh = { onRefreshModels(connection.id) {} },
                )
            }
            HorizontalDivider()
            Text("应用外观", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                listOf("system" to "◐ 系统", "light" to "☀ 浅色", "dark" to "☾ 深色").forEach { (value, label) ->
                    FilterChip(label = label, selected = state.snapshot.preferences.themeMode == value, onClick = { onThemeSelected(value) }, modifier = Modifier.weight(1f))
                }
            }
            HorizontalDivider()
            Text("数据和隐私", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "对话、图片与 API 配置仅储存在应用私有区域，Android 备份已停用。发送前会显示服务商及所传内容。API Key 由 Android Keystore 加密保护。",
                fontSize = 12.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider()
            Text("系统权限", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            TextButton(onClick = { openAppNotificationSettings(context) }, modifier = Modifier.fillMaxWidth()) {
                Text("设置后台完成通知权限")
            }
            TextButton(onClick = { openAppSettings(context) }, modifier = Modifier.fillMaxWidth()) {
                Text("设置相机权限")
            }
            Text("外观动画以轻量圆角、透明层次及关键转场为主。", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    pendingDelete?.let { connection ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除 API 配置？") },
            text = { Text("删除后不可使用“${connection.connectionDisplayName()}”继续发送新消息。现有会话仍保留。") },
            confirmButton = { TextButton(onClick = { onDeleteConnection(connection); pendingDelete = null }) { Text("删除") } },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("取消") } },
        )
    }
}

/** 显示已有 API 连接、已发现模型及刷新或编辑入口。 */
@Composable
private fun ConnectionSettingsCard(
    connection: ProviderConnectionInfo,
    isSelected: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onRefresh: () -> Unit,
) {
    Card(shape = RoundedCornerShape(19.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                ProviderLogo(connection.kind, connection.connectionDisplayName(), 34.dp)
                Column(Modifier.weight(1f)) {
                    Text(connection.connectionDisplayName(), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        when {
                            !connection.credentialsAvailable -> "请重新录入 API Key"
                            connection.lastValidatedAt != null -> "${connection.models.size} 个可用模型"
                            else -> "尚未验证连接"
                        },
                        fontSize = 10.sp,
                        color = if (connection.credentialsAvailable) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                    )
                }
                if (isSelected) StatusPill("当前", "当前模型服务商", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
            }
            if (connection.models.isNotEmpty()) {
                Text(connection.models.take(3).joinToString(" · ") { it.name }.let { if (connection.models.size > 3) "$it +${connection.models.size - 3}" else it }, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                TextButton(onClick = onEdit) { Text("编辑") }
                TextButton(onClick = onRefresh) { Text("刷新模型") }
                TextButton(onClick = onDelete) { Text("删除", color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

/** 提供相册或相机的快捷选择并反馈模型图片能力。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImageSourceSheet(canAddImage: Boolean, onGallery: () -> Unit, onCamera: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(bottom = 38.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("添加图片", fontSize = 21.sp, fontWeight = FontWeight.Bold)
            if (!canAddImage) Text("当前模型不支持图片输入，请先切换至识图模型。", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
            Button(onClick = onGallery, enabled = canAddImage, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(15.dp)) { Text("▧  从相册选择") }
            OutlinedButton(onClick = onCamera, enabled = canAddImage, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(15.dp)) { Text("◉  使用相机拍照") }
            Spacer(Modifier.height(6.dp))
        }
    }
}

/** 编辑可读的会话标题。 */
@Composable
private fun RenameSessionDialog(session: ChatSession, onDismiss: () -> Unit, onRename: (String) -> Unit) {
    var title by rememberSaveable(session.id) { mutableStateOf(session.title) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("重命名对话") },
        text = { OutlinedTextField(value = title, onValueChange = { title = it.take(56) }, label = { Text("对话名称") }, singleLine = true) },
        confirmButton = { TextButton(onClick = { onRename(title) }) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/** 输入服务商模型目录中尚未列出的模型 ID。 */
@Composable
private fun ManualModelDialog(providerName: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var modelId by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("输入模型 ID") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(modelId, { modelId = it }, label = { Text("模型 ID") }, singleLine = true, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false))
                Text("${providerName}可能无法自动验证手动输入模型的图片和思考能力。", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(modelId) }) { Text("使用") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/** 显示欢迎消息和当前模型使用提示。 */
@Composable
private fun WelcomeGreeting(hasProvider: Boolean, modelName: String?) {
    Column(
        Modifier.fillMaxSize().padding(26.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(66.dp).clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
            Text("✳", fontSize = 32.sp, color = MaterialTheme.colorScheme.primary)
        }
        Text("你想聊点什么？", Modifier.padding(top = 19.dp), fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(
            if (!hasProvider) "添加 API Key 并选择模型，开始你的第一个对话。" else "你的消息仅在发送时传输给所选 AI 服务商。",
            Modifier.padding(top = 8.dp).widthIn(max = 300.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
            lineHeight = 20.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        modelName?.let {
            Surface(Modifier.padding(top = 17.dp), shape = RoundedCornerShape(30.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)) {
                Text("✳  $it 已就绪", Modifier.padding(horizontal = 14.dp, vertical = 8.dp), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** 展示初次接入模型的明确操作按钮。 */
@Composable
private fun SetupWelcomeCard(configuredProviders: Int, onSetup: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text(if (configuredProviders == 0) "连接你的第一个模型" else "选择可用的服务商模型", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        Text("API Key 使用 Android Keystore 加密保存在本机。", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = onSetup, shape = RoundedCornerShape(15.dp)) { Text("设置 API") }
    }
}

/** 提示用户选择或手动添加首个服务商模型。 */
@Composable
private fun SelectFirstModelCard(onChooseModel: () -> Unit, onAddModel: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 9.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        Button(onClick = onChooseModel, modifier = Modifier.weight(1f)) { Text("选择模型") }
        OutlinedButton(onClick = onAddModel, modifier = Modifier.weight(1f)) { Text("管理 API") }
    }
}

/** 显示可滑动加载状态并为消息列表预留空间。 */
@Composable
private fun LoadingMessages() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.size(25.dp), strokeWidth = 2.dp)
    }
}

/** 在对话页展示可单独关闭的操作成功或失败反馈。 */
@Composable
private fun FeedbackCard(text: String, isError: Boolean, onDismiss: () -> Unit) {
    Surface(
        Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 4.dp),
        shape = RoundedCornerShape(15.dp),
        color = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
    ) {
        Row(Modifier.padding(start = 13.dp, end = 3.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, Modifier.weight(1f), fontSize = 12.sp, color = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer)
            IconButton(onClick = onDismiss) { Text("×") }
        }
    }
}

/** 根据当前主题展示提供商官方图标，未知兼容接口继续使用名称字标。 */
@Composable
private fun ProviderLogo(
    kind: ProviderKind?,
    label: String,
    size: Dp,
    modelId: String? = null,
    modelName: String? = null,
) {
    val recognizedProvider = if (kind == ProviderKind.CUSTOM || kind == null) {
        ModelProviderCatalog.identify(kind, modelId ?: label, modelName ?: label)
    } else null
    val logoKind = when (recognizedProvider) {
        ModelProvider.OPENAI -> ProviderKind.OPENAI
        ModelProvider.ANTHROPIC -> ProviderKind.ANTHROPIC
        ModelProvider.GOOGLE -> ProviderKind.GEMINI
        ModelProvider.DEEPSEEK -> ProviderKind.DEEPSEEK
        else -> kind
    }
    val isDarkBackground = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val iconResource = when (logoKind) {
        ProviderKind.OPENAI -> if (isDarkBackground) R.drawable.ic_provider_openai_dark else R.drawable.ic_provider_openai_light
        ProviderKind.ANTHROPIC -> if (isDarkBackground) R.drawable.ic_provider_anthropic_dark else R.drawable.ic_provider_anthropic_light
        ProviderKind.GEMINI -> if (isDarkBackground) R.drawable.ic_provider_gemini_dark else R.drawable.ic_provider_gemini_light
        ProviderKind.DEEPSEEK -> if (isDarkBackground) R.drawable.ic_provider_deepseek_dark else R.drawable.ic_provider_deepseek_light
        else -> null
    }
    if (iconResource != null) {
        Image(
            painter = painterResource(iconResource),
            contentDescription = null,
            modifier = Modifier.size(size).semantics { contentDescription = "$label 图标" },
        )
    } else {
        val logo = label.firstOrNull()?.uppercaseChar()?.toString().orEmpty().ifBlank { "AI" }
        val color = MaterialTheme.colorScheme.primary
        Box(
            Modifier.size(size).clip(CircleShape).background(color.copy(alpha = 0.13f)).semantics { contentDescription = "$label 图标" },
            contentAlignment = Alignment.Center,
        ) {
            Text(logo, color = color, fontSize = (size.value * 0.52).sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** 创建圆形工具按钮和屏幕阅读器可访问描述。 */
@Composable
private fun CircleIconButton(label: String, glyph: String, onClick: () -> Unit, size: Dp = 43.dp) {
    Surface(
        modifier = Modifier.size(size).clip(CircleShape).clickable(onClick = onClick).semantics { contentDescription = label },
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
        shape = CircleShape,
    ) {
        Box(contentAlignment = Alignment.Center) { Text(glyph, color = MaterialTheme.colorScheme.onSurface, fontSize = (size.value * 0.51).sp, fontWeight = FontWeight.Normal) }
    }
}

/** 使用系统相册拾取图片并在 IO 线程保存到应用私有目录。 */
private fun importPhoto(viewModel: ChatViewModel, uri: Uri, onSuccess: (PendingAttachment) -> Unit) {
    viewModel.importPhoto(uri, onSuccess)
}

/** 在 Android 公共相机 Activity 中拍摄一张临时照片。 */
private fun openCamera(context: android.content.Context, onUriReady: (Uri) -> Unit) {
    val directory = File(context.cacheDir, "camera").apply { mkdirs() }
    val file = File(directory, "camera-capture-${java.util.UUID.randomUUID()}.jpg")
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    onUriReady(uri)
}

/** 在首次提交聊天任务时请求通知权限，不因此阻止发送模型请求。 */
private fun requestNotificationPermissionIfNeeded(
    context: android.content.Context,
    launcher: androidx.activity.result.ActivityResultLauncher<String>,
    previouslyRequested: Boolean,
) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || previouslyRequested) return
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
    launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
}

/** 在 Android 设置中打开本应用权限管理页面。 */
private fun openAppSettings(context: android.content.Context) {
    context.startActivity(
        android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

/** 打开本应用专属通知设置，不支持时回退到系统应用设置。 */
private fun openAppNotificationSettings(context: android.content.Context) {
    val intent = android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }.onFailure { openAppSettings(context) }
}

/** 在发送前预处理易读的 Markdown 文本及代码格式。 */
@Composable
private fun richText(value: String): AnnotatedString {
    val renderedValue = renderMathMarkup(value)
    val codeBlocks = Regex("(?s)```([^\\n]*)\\n(.*?)```")
    val inlineFormatting = Regex("\\*\\*(.+?)\\*\\*|`([^`]+)`")
    return buildAnnotatedString {
        @Composable
        fun appendFormattedText(text: String) {
            var cursor = 0
            inlineFormatting.findAll(text).forEach { match ->
                if (match.range.first > cursor) append(text.substring(cursor, match.range.first))
                val code = match.groups[2]
                if (code != null) {
                    val start = length
                    append(code.value)
                    addStyle(SpanStyle(fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary), start, length)
                } else {
                    val bold = match.groups[1]?.value.orEmpty()
                    val start = length
                    append(bold)
                    addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, length)
                }
                cursor = match.range.last + 1
            }
            if (cursor < text.length) append(text.substring(cursor))
        }

        var cursor = 0
        codeBlocks.findAll(renderedValue).forEach { match ->
            if (match.range.first > cursor) appendFormattedText(renderedValue.substring(cursor, match.range.first))
            val code = match.groups[2]?.value.orEmpty()
            val start = length
            append(code)
            addStyle(
                SpanStyle(fontFamily = FontFamily.Monospace, background = MaterialTheme.colorScheme.surfaceVariant, color = MaterialTheme.colorScheme.primary),
                start,
                length,
            )
            cursor = match.range.last + 1
        }
        if (cursor < renderedValue.length) appendFormattedText(renderedValue.substring(cursor))
    }
}

/** 将常用 LaTeX 数学分隔符和命令转换为可直接阅读的数学符号。 */
private fun renderMathMarkup(value: String): String {
    val superscriptCharacters = mapOf(
        '0' to '⁰', '1' to '¹', '2' to '²', '3' to '³', '4' to '⁴',
        '5' to '⁵', '6' to '⁶', '7' to '⁷', '8' to '⁸', '9' to '⁹',
        '+' to '⁺', '-' to '⁻', '=' to '⁼', '(' to '⁽', ')' to '⁾',
        'n' to 'ⁿ', 'i' to 'ⁱ', 'T' to 'ᵀ', 'A' to 'ᴬ', 'B' to 'ᴮ',
    )
    val subscriptCharacters = mapOf(
        '0' to '₀', '1' to '₁', '2' to '₂', '3' to '₃', '4' to '₄',
        '5' to '₅', '6' to '₆', '7' to '₇', '8' to '₈', '9' to '₉',
        '+' to '₊', '-' to '₋', '=' to '₌', '(' to '₍', ')' to '₎',
    )

    var rendered = value
        .replace("\\[", "").replace("\\]", "")
        .replace("\\(", "").replace("\\)", "")
        .replace(Regex("(?<!\\\\)\\$"), "")
        .replace(Regex("\\\\(?:left|right)\\b"), "")

    val fractions = Regex("\\\\frac\\s*\\{([^{}]*)}\\s*\\{([^{}]*)}")
    rendered = fractions.replace(rendered) { match -> "${match.groupValues[1]}⁄${match.groupValues[2]}" }
    val boxedValues = Regex("\\\\boxed\\s*\\{([^{}]*)}")
    rendered = boxedValues.replace(rendered) { match -> "[${match.groupValues[1]}]" }

    val commands = mapOf(
        "\\times" to "×", "\\cdot" to "·", "\\div" to "÷",
        "\\pm" to "±", "\\mp" to "∓", "\\leq" to "≤",
        "\\le" to "≤", "\\geq" to "≥", "\\ge" to "≥",
        "\\neq" to "≠", "\\ne" to "≠", "\\approx" to "≈",
        "\\infty" to "∞", "\\pi" to "π", "\\sqrt" to "√",
        "\\sum" to "∑", "\\prod" to "∏", "\\int" to "∫",
        "\\rightarrow" to "→", "\\to" to "→", "\\alpha" to "α",
        "\\beta" to "β", "\\theta" to "θ", "\\lambda" to "λ",
        "\\mu" to "μ", "\\sigma" to "σ", "\\Delta" to "Δ",
    )
    commands.forEach { (command, symbol) -> rendered = rendered.replace(command, symbol) }
    rendered = rendered.replace(Regex("\\\\(?:text|mathrm|mathbf)\\s*\\{([^{}]*)}"), "$1")
    rendered = rendered.replace(Regex("\\\\(?:,|;|!)"), " ")

    rendered = Regex("\\^\\s*(?:\\{([^{}]*)}|([\\w+\\-=()]))").replace(rendered) { match ->
        val exponent = match.groups[1]?.value ?: match.groups[2]?.value.orEmpty()
        exponent.map { superscriptCharacters[it] ?: it }.joinToString("")
    }
    rendered = Regex("_\\s*(?:\\{([^{}]*)}|([\\w+\\-=()]))").replace(rendered) { match ->
        val index = match.groups[1]?.value ?: match.groups[2]?.value.orEmpty()
        index.map { subscriptCharacters[it] ?: it }.joinToString("")
    }
    return rendered.replace("{", "").replace("}", "").replace(Regex("\\\\([{}])"), "$1")
}

/** 计算正在生成任务的简洁耗时标签。 */
private fun elapsedLabel(startedAt: Long, now: Long): String = "${((now - startedAt).coerceAtLeast(0) / 1000)} 秒"

/** 显示已配置连接名称，并迁移旧版固定服务商名称。 */
private fun ProviderConnectionInfo.connectionDisplayName(): String =
    name.connectionDisplayName() ?: kind.displayName

/** 将旧版固定自定义类型名称转换为兼容接口名称。 */
private fun String?.connectionDisplayName(): String? = when {
    this == "自定义接口" -> "兼容接口"
    isNullOrBlank() -> null
    else -> this
}

/** 显示当前模型能力和生成状态的半透明小型标签。 */
@Composable
private fun StatusPill(text: String, description: String, background: Color, foreground: Color) {
    Surface(color = background, shape = RoundedCornerShape(30.dp), modifier = Modifier.semantics { contentDescription = "$description：$text" }) {
        Text(text, Modifier.padding(horizontal = 8.dp, vertical = 5.dp), color = foreground, fontSize = 9.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

/** 按当前模型选项显示可点击的胶囊控件和可识别的服务商图标。 */
@Composable
private fun CompactChip(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    providerKind: ProviderKind? = null,
    providerLabel: String? = null,
    providerModelId: String? = null,
) {
    Surface(
        modifier = modifier.clip(RoundedCornerShape(30.dp)).clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(30.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (enabled) 0.76f else 0.3f),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.44f)),
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            if (providerKind != null) ProviderLogo(providerKind, providerLabel ?: label, 14.dp, providerModelId, label)
            Text(label, fontSize = 10.sp, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** 渲染可快速选择模型原生思考强度的控件。 */
@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.clip(RoundedCornerShape(24.dp)).clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
    ) { Text(label, Modifier.padding(horizontal = 10.dp, vertical = 6.dp), fontSize = 10.sp, color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant) }
}

/** 设置页面里记录清晰的布尔连接能力声明。 */
@Composable
private fun PreferenceToggleRow(title: String, selected: Boolean, onClick: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).clickable { onClick(!selected) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), fontSize = 13.sp)
        FilterChip(label = if (selected) "已开启" else "关闭", selected = selected, onClick = { onClick(!selected) })
    }
}

/** 显示历史会话的最后活跃本地时间。 */
private fun Long.displayTimestamp(): String = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(this))

/** 通过 Compose 的可见性状态为输入栏的 ime 预留系统安全区。 */
