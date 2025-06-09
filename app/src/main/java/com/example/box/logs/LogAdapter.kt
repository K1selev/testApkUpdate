package com.example.box.logs

//class LogAdapter : RecyclerView.Adapter<LogViewHolder>() {
//    private var logs: List<LogEntity> = listOf()
//
//    fun submitList(data: List<LogEntity>) {
//        logs = data
//        notifyDataSetChanged()
//    }
//
//    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
////        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_log, parent, false)
//        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_log, parent, false)
//        return LogViewHolder(view)
//    }
//
//    override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
//        holder.bind(logs[position])
//    }
//
//    override fun getItemCount() = logs.size
//}
//
//class LogViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
//    fun bind(log: LogEntity) {
//        itemView.findViewById<TextView>(R.id.tvMessage).text = log.message
//    }
//}


//package com.example.box

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.box.R

class LogAdapter : RecyclerView.Adapter<LogViewHolder>() {
    private var logs: List<LogEntity> = emptyList()

    fun submitList(data: List<LogEntity>) {
        logs = data
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_log, parent, false)
        return LogViewHolder(view)
    }

    override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
        holder.bind(logs[position])
    }

    override fun getItemCount(): Int = logs.size
}

class LogViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    fun bind(log: LogEntity) {
        itemView.findViewById<TextView>(R.id.tvMessage).text = log.message
    }
}