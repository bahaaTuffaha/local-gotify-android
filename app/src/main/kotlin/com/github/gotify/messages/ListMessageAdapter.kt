package com.github.gotify.messages

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.text.Spannable
import android.text.SpannableString
import android.text.format.DateUtils
import android.text.style.BackgroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import coil.ImageLoader
import coil.load
import com.github.gotify.MarkwonFactory
import com.github.gotify.R
import com.github.gotify.Settings
import com.github.gotify.Utils
import com.github.gotify.databinding.MessageItemBinding
import com.github.gotify.databinding.MessageItemCompactBinding
import com.github.gotify.messages.provider.MessageWithImage
import io.noties.markwon.Markwon
import java.text.DateFormat
import java.util.Date
import org.threeten.bp.OffsetDateTime

internal class ListMessageAdapter(
    private val context: Context,
    private val settings: Settings,
    private val imageLoader: ImageLoader,
    private val delete: Delete,
    private val favorite: Favorite
) : ListAdapter<MessageWithImage, ListMessageAdapter.ViewHolder>(DiffCallback) {
    private val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val markwon: Markwon = MarkwonFactory.createForMessage(context, imageLoader)
    private var searchQuery: String = ""

    private val timeFormatRelative =
        context.resources.getString(R.string.time_format_value_relative)
    private val timeFormatPrefsKey = context.resources.getString(R.string.setting_key_time_format)

    private var messageLayout = 0

    init {
        val messageLayoutPrefsKey = context.resources.getString(R.string.setting_key_message_layout)
        val messageLayoutNormal = context.resources.getString(R.string.message_layout_value_normal)
        val messageLayoutSetting = prefs.getString(messageLayoutPrefsKey, messageLayoutNormal)

        setHasStableIds(true)

        messageLayout = if (messageLayoutSetting == messageLayoutNormal) {
            R.layout.message_item
        } else {
            R.layout.message_item_compact
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val layoutInflater = LayoutInflater.from(parent.context)
        return if (messageLayout == R.layout.message_item) {
            val binding = MessageItemBinding.inflate(layoutInflater, parent, false)
            ViewHolder(binding)
        } else {
            val binding = MessageItemCompactBinding.inflate(layoutInflater, parent, false)
            ViewHolder(binding)
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val message = currentList[position]
        holder.message.autoLinkMask = 0
        if (message.isPlaceholder) {
            holder.title.text = context.getString(R.string.missing_message_title)
            holder.message.text = context.getString(R.string.missing_message_body, message.message.id)
        } else {
            markwon.setMarkdown(holder.message, message.message.message)
            holder.title.text = message.message.title
        }
        highlightSearchQuery(holder.message)
        highlightSearchQuery(holder.title)

        if (message.image != null) {
            val url = Utils.resolveAbsoluteUrl("${settings.url}/", message.image)
            holder.image.load(url, imageLoader) {
                error(R.drawable.ic_alarm)
                placeholder(R.drawable.ic_placeholder)
            }
        } else {
            holder.image.setImageResource(R.drawable.ic_placeholder)
        }

        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val timeFormat = prefs.getString(timeFormatPrefsKey, timeFormatRelative)
        holder.setDateTime(message.message.date, timeFormat == timeFormatRelative)
        holder.date.setOnClickListener {
            if (!message.isPlaceholder && message.message.date != null) {
                holder.switchTimeFormat()
            }
        }
        holder.favorite.setImageResource(
            if (message.isFavorite) R.drawable.ic_star else R.drawable.ic_star_border
        )
        holder.favorite.contentDescription = context.getString(
            if (message.isFavorite) R.string.unfavorite_message else R.string.favorite_message
        )
        holder.itemView.alpha = if (message.isRead) 0.72f else 1f

        holder.delete.setOnClickListener {
            delete.delete(message)
        }
        holder.favorite.setOnClickListener {
            favorite.toggle(message)
        }
    }

    override fun getItemId(position: Int): Long {
        val currentItem = currentList[position]
        return currentItem.message.id
    }

    // Fix for message not being selectable (https://issuetracker.google.com/issues/37095917)
    override fun onViewAttachedToWindow(holder: ViewHolder) {
        super.onViewAttachedToWindow(holder)
        holder.message.isEnabled = false
        holder.message.isEnabled = true
    }

    class ViewHolder(binding: ViewBinding) : RecyclerView.ViewHolder(binding.root) {
        lateinit var image: ImageView
        lateinit var message: TextView
        lateinit var title: TextView
        lateinit var date: TextView
        lateinit var delete: ImageButton
        lateinit var favorite: ImageButton

        private var relativeTimeFormat = true
        private var dateTime: OffsetDateTime? = null

        init {
            enableCopyToClipboard()
            if (binding is MessageItemBinding) {
                image = binding.messageImage
                message = binding.messageText
                title = binding.messageTitle
                date = binding.messageDate
                delete = binding.messageDelete
                favorite = binding.messageFavorite
            } else if (binding is MessageItemCompactBinding) {
                image = binding.messageImage
                message = binding.messageText
                title = binding.messageTitle
                date = binding.messageDate
                delete = binding.messageDelete
                favorite = binding.messageFavorite
            }
        }

        fun switchTimeFormat() {
            relativeTimeFormat = !relativeTimeFormat
            updateDate()
        }

        fun setDateTime(dateTime: OffsetDateTime?, relativeTimeFormatPreference: Boolean) {
            this.dateTime = dateTime
            relativeTimeFormat = relativeTimeFormatPreference
            updateDate()
        }

        private fun updateDate() {
            val currentDateTime = dateTime
            if (currentDateTime == null) {
                date.text = itemView.context.getString(R.string.message_not_found_date)
                return
            }
            val text = if (relativeTimeFormat) {
                // Relative time format
                Utils.dateToRelative(currentDateTime)
            } else {
                // Absolute time format
                val time = currentDateTime.toInstant().toEpochMilli()
                val date = Date(time)
                if (DateUtils.isToday(time)) {
                    DateFormat.getTimeInstance(DateFormat.SHORT).format(date)
                } else {
                    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(date)
                }
            }

            date.text = text
        }

        private fun enableCopyToClipboard() {
            super.itemView.setOnLongClickListener { view: View ->
                val clipboard = view.context
                    .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager?
                val clip = ClipData.newPlainText("GotifyMessageContent", message.text.toString())
                if (clipboard != null) {
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(
                        view.context,
                        view.context.getString(R.string.message_copied_to_clipboard),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                true
            }
        }
    }

    object DiffCallback : DiffUtil.ItemCallback<MessageWithImage>() {
        override fun areItemsTheSame(
            oldItem: MessageWithImage,
            newItem: MessageWithImage
        ): Boolean {
            return oldItem.message.id == newItem.message.id
        }

        override fun areContentsTheSame(
            oldItem: MessageWithImage,
            newItem: MessageWithImage
        ): Boolean {
            return oldItem == newItem
        }
    }

    fun interface Delete {
        fun delete(message: MessageWithImage)
    }

    fun interface Favorite {
        fun toggle(message: MessageWithImage)
    }

    fun updateSearchQuery(query: String) {
        this.searchQuery = query
    }

    private fun highlightSearchQuery(textView: TextView) {
        if (searchQuery.isEmpty()) return

        val text = textView.text ?: return
        val spannableString = if (text is Spannable) {
            text
        } else {
            SpannableString(text)
        }

        var startIndex = text.indexOf(searchQuery, ignoreCase = true)
        while (startIndex != -1) {
            val endIndex = startIndex + searchQuery.length
            spannableString.setSpan(
                BackgroundColorSpan(Color.YELLOW),
                startIndex,
                endIndex,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            startIndex = text.indexOf(searchQuery, endIndex, ignoreCase = true)
        }
        textView.text = spannableString
    }
}
