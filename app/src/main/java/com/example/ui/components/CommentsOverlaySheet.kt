package com.example.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Comment
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import com.example.data.local.Comment
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CommentsOverlaySheet(
    commentsList: List<Comment>,
    isLoggedIn: Boolean,
    isAdmin: Boolean = false,
    isLoading: Boolean = false,
    currentUsername: String,
    heightFraction: Float = 0.85f,
    onDismiss: () -> Unit,
    onPostComment: (text: String, parentId: String?) -> Unit,
    onLikeClick: (id: Int, isLiked: Boolean) -> Unit,
    onDeleteClick: (id: Int) -> Unit
) {
    val view = LocalView.current
    var newCommentText by remember { mutableStateOf("") }
    var replyingTo by remember { mutableStateOf<Comment?>(null) }
    var isInputFocused by remember { mutableStateOf(false) }
    val currentHeightFraction = if (isInputFocused) 0.98f else heightFraction
    
    var dragOffsetY by remember { mutableStateOf(0f) }
    val animatedOffsetY by animateFloatAsState(targetValue = dragOffsetY, label = "dragOffset")

    val topLevel = remember(commentsList) {
        commentsList.filter { it.parentId == null }.sortedByDescending { it.timestamp }
    }
    val repliesMap = remember(commentsList) {
        commentsList.filter { it.parentId != null }
            .groupBy { it.parentId }
            .mapValues { entry -> entry.value.sortedBy { it.timestamp } }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            ),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(currentHeightFraction)
                .graphicsLayer {
                    translationY = animatedOffsetY
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragEnd = {
                            if (dragOffsetY > 250f) {
                                onDismiss()
                            } else {
                                dragOffsetY = 0f
                            }
                        },
                        onDragCancel = {
                            dragOffsetY = 0f
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            if (dragAmount.y > 0 || dragOffsetY > 0) {
                                dragOffsetY = (dragOffsetY + dragAmount.y).coerceAtLeast(0f)
                            }
                        }
                    )
                }
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .background(Color(0xFF0F1115))
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
                .statusBarsPadding()
        ) {
            // Drag handle
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(vertical = 12.dp)
                    .size(width = 40.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.15f))
            )

            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${commentsList.filter { it.parentId == null }.size} Comments",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    if (isLoading) {
                        Spacer(Modifier.width(10.dp))
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.05f))

            // Comments list
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (!isLoading && commentsList.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.Comment,
                            contentDescription = null,
                            tint = Color.Gray.copy(alpha = 0.4f),
                            modifier = Modifier.size(54.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text("No comments yet.", color = Color.White,
                            style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text("Be the first to share your thoughts!", color = Color.Gray,
                            style = MaterialTheme.typography.bodySmall)
                    }
                } else {
                    val expandedReplies = remember { mutableStateMapOf<String, Boolean>() }
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        topLevel.forEach { parent ->
                            item(key = parent.id) {
                                CommentOverlayItemRow(
                                    comment = parent,
                                    canDelete = parent.username == currentUsername || isAdmin,
                                    isReply = false,
                                    onLikeClick = { onLikeClick(parent.id, parent.isLikedByUser) },
                                    onDeleteClick = { onDeleteClick(parent.id) },
                                    onReplyClick = {
                                        if (replyingTo?.id == parent.id) {
                                            replyingTo = null
                                            if (newCommentText == "@${parent.username} ") {
                                                newCommentText = ""
                                            }
                                        } else {
                                            replyingTo = parent
                                            newCommentText = "@${parent.username} "
                                        }
                                    }
                                )
                            }
                            val replies = repliesMap[parent.remoteId] ?: emptyList()
                            val isExpanded = expandedReplies[parent.remoteId] ?: false
                            val repliesToShow = if (isExpanded || replies.size <= 2) {
                                replies
                            } else {
                                replies.take(1)
                            }

                            repliesToShow.forEachIndexed { index, reply ->
                                val isLast = index == repliesToShow.size - 1 && (replies.size <= 2 || isExpanded)
                                item(key = "reply_${reply.id}") {
                                    CommentOverlayItemRow(
                                        comment = reply,
                                        canDelete = reply.username == currentUsername || isAdmin,
                                        isReply = true,
                                        isLastReply = isLast,
                                        onLikeClick = { onLikeClick(reply.id, reply.isLikedByUser) },
                                        onDeleteClick = { onDeleteClick(reply.id) },
                                        onReplyClick = null
                                    )
                                }
                            }

                            if (replies.size > 2) {
                                if (!isExpanded) {
                                    item(key = "view_more_${parent.id}") {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { expandedReplies[parent.remoteId] = true }
                                                .padding(start = 52.dp, end = 20.dp, top = 4.dp, bottom = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .width(24.dp)
                                                    .height(1.dp)
                                                    .background(Color.White.copy(alpha = 0.15f))
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                text = "View replies (${replies.size - 1})",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.Gray
                                            )
                                        }
                                    }
                                } else {
                                    item(key = "hide_${parent.id}") {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { expandedReplies[parent.remoteId] = false }
                                                .padding(start = 52.dp, end = 20.dp, top = 4.dp, bottom = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .width(24.dp)
                                                    .height(1.dp)
                                                    .background(Color.White.copy(alpha = 0.15f))
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                text = "Hide replies",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.Gray
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.05f))

            if (isLoggedIn) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0F1115))
                        .navigationBarsPadding()
                        .imePadding()
                ) {
                    AnimatedVisibility(visible = replyingTo != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF1A1D23))
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Replying to @${replyingTo?.username}",
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.weight(1f))
                            Icon(Icons.Default.Close, contentDescription = "Cancel reply",
                                tint = Color.Gray,
                                modifier = Modifier.size(16.dp).clickable {
                                    if (newCommentText == "@${replyingTo?.username} ") {
                                        newCommentText = ""
                                    }
                                    replyingTo = null
                                })
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = newCommentText,
                            onValueChange = { newCommentText = it },
                            placeholder = {
                                Text(
                                    if (replyingTo != null) "Write a reply..." else "Add comment...",
                                    color = Color.Gray, fontSize = 14.sp
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .onFocusChanged { focusState ->
                                    isInputFocused = focusState.isFocused
                                },
                            shape = RoundedCornerShape(28.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color(0xFF14171C),
                                unfocusedContainerColor = Color(0xFF14171C),
                                unfocusedBorderColor = Color.Transparent,
                                focusedBorderColor = Color.Transparent
                            ),
                            singleLine = false,
                            maxLines = 4,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White)
                        )
                        Spacer(Modifier.width(12.dp))
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(
                                    if (newCommentText.isNotBlank()) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                                )
                                .clickable(enabled = newCommentText.isNotBlank()) {
                                    val parentId = replyingTo?.remoteId?.takeIf { it.isNotEmpty() }
                                    onPostComment(newCommentText.trim(), parentId)
                                    newCommentText = ""
                                    replyingTo = null
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Send, contentDescription = "Send",
                                tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0F1115))
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                        .navigationBarsPadding(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Join the discussion!", style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold, color = Color.White)
                        Spacer(Modifier.height(4.dp))
                        Text("Only logged in users can add comments.",
                            style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                }
            }
        }
    }
}

@Composable
fun buildAnnotatedCommentText(text: String): androidx.compose.ui.text.AnnotatedString {
    return buildAnnotatedString {
        val words = text.split(" ")
        words.forEachIndexed { index, word ->
            if (word.startsWith("@") && word.length > 1) {
                var cleanWord = word
                var suffix = ""
                val lastChar = word.last()
                if (!lastChar.isLetterOrDigit() && lastChar != '_' && lastChar != '-') {
                    cleanWord = word.dropLast(1)
                    suffix = lastChar.toString()
                }

                withStyle(
                    SpanStyle(
                        color = Color(0xFF3897F0), // Instagram blue for mentions
                        fontWeight = FontWeight.Bold
                    )
                ) {
                    append(cleanWord)
                }
                if (suffix.isNotEmpty()) {
                    append(suffix)
                }
            } else {
                append(word)
            }
            if (index < words.size - 1) {
                append(" ")
            }
        }
    }
}

@Composable
fun CommentOverlayItemRow(
    comment: Comment,
    canDelete: Boolean,
    isReply: Boolean = false,
    isLastReply: Boolean = false,
    onLikeClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onReplyClick: (() -> Unit)?
) {
    if (isReply) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .padding(end = 20.dp)
        ) {
            // Thread line connector
            Box(
                modifier = Modifier
                    .width(52.dp)
                    .fillMaxHeight()
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val startX = 39.dp.toPx() // Align perfectly with parent avatar center
                    val replyAvatarCenterY = 25.dp.toPx() // Align with reply avatar center (10dp padding + 15dp half avatar)
                    val strokeWidth = 1.5.dp.toPx()
                    val lineColor = Color.White.copy(alpha = 0.15f)

                    // Vertical line connecting parent
                    val endY = if (isLastReply) replyAvatarCenterY else size.height
                    drawLine(
                        color = lineColor,
                        start = Offset(startX, 0f),
                        end = Offset(startX, endY),
                        strokeWidth = strokeWidth
                    )

                    // Horizontal curve/bend line connecting reply avatar
                    drawLine(
                        color = lineColor,
                        start = Offset(startX, replyAvatarCenterY),
                        end = Offset(46.dp.toPx(), replyAvatarCenterY),
                        strokeWidth = strokeWidth
                    )
                }
            }

            // Reply content
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(top = 10.dp, bottom = 10.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(
                        model = comment.userAvatar,
                        contentDescription = comment.username,
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(Color.Gray.copy(alpha = 0.2f)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = comment.username,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    if (comment.isAuthorAdmin) {
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.Verified,
                            contentDescription = "Admin",
                            tint = Color(0xFF1D9BF0),
                            modifier = Modifier.size(13.dp)
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    if (canDelete) {
                        IconButton(onClick = onDeleteClick, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete",
                                tint = Color.Gray.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
                        }
                    }
                }

                Spacer(Modifier.height(6.dp))

                Text(
                    text = buildAnnotatedCommentText(comment.text),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.9f),
                    lineHeight = 18.sp
                )

                Spacer(Modifier.height(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null, onClick = onLikeClick
                        )
                    ) {
                        Icon(
                            imageVector = if (comment.isLikedByUser) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = if (comment.isLikedByUser) "Liked" else "Like",
                            tint = if (comment.isLikedByUser) MaterialTheme.colorScheme.primary else Color.Gray,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            comment.likes.toString(),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (comment.isLikedByUser) MaterialTheme.colorScheme.primary else Color.Gray,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    if (onReplyClick != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable(
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                indication = null, onClick = onReplyClick
                            )
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = "Reply",
                                tint = Color.Gray, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(5.dp))
                            val label = if (comment.replyCount > 0) "Reply (${comment.replyCount})" else "Reply"
                            Text(
                                label,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Text(
                        text = relativeTime(comment.timestamp),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = 20.dp,
                    end = 20.dp,
                    top = 14.dp,
                    bottom = 14.dp
                )
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = comment.userAvatar,
                    contentDescription = comment.username,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color.Gray.copy(alpha = 0.2f)),
                    contentScale = ContentScale.Crop
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = comment.username,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                if (comment.isAuthorAdmin) {
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.Verified,
                        contentDescription = "Admin",
                        tint = Color(0xFF1D9BF0),
                        modifier = Modifier.size(15.dp)
                    )
                }
                Spacer(Modifier.weight(1f))
                if (canDelete) {
                    IconButton(onClick = onDeleteClick, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete",
                            tint = Color.Gray.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Text(
                text = buildAnnotatedCommentText(comment.text),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.9f),
                lineHeight = 20.sp
            )

            Spacer(Modifier.height(10.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null, onClick = onLikeClick
                    )
                ) {
                    Icon(
                        imageVector = if (comment.isLikedByUser) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = if (comment.isLikedByUser) "Liked" else "Like",
                        tint = if (comment.isLikedByUser) MaterialTheme.colorScheme.primary else Color.Gray,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        comment.likes.toString(),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (comment.isLikedByUser) MaterialTheme.colorScheme.primary else Color.Gray,
                        fontWeight = FontWeight.Medium
                    )
                }

                if (onReplyClick != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null, onClick = onReplyClick
                        )
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = "Reply",
                            tint = Color.Gray, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(5.dp))
                        val label = if (comment.replyCount > 0) "Reply (${comment.replyCount})" else "Reply"
                        Text(
                            label,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Text(
                    text = relativeTime(comment.timestamp),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
        }
    }
}

private fun relativeTime(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    return when {
        diff < 60_000     -> "Just now"
        diff < 3_600_000  -> "${diff / 60_000}m ago"
        diff < 86_400_000 -> "${diff / 3_600_000}h ago"
        else -> SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(timestamp))
    }
}
