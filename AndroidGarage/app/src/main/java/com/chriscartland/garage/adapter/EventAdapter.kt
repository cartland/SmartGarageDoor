/*
 * Copyright 2021 Chris Cartland. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.chriscartland.garage.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.chriscartland.garage.R
import com.chriscartland.garage.model.DoorData
import com.chriscartland.garage.model.DoorDisplayInfo

class EventAdapter(
    private val context: Context,
    var items: List<DoorData>
) : RecyclerView.Adapter<EventAdapter.EventViewHolder>() {

    class EventViewHolder(private val view: View) : RecyclerView.ViewHolder(view) {
        val eventIcon: ImageView = view.findViewById(R.id.event_icon)
        val titleText: TextView = view.findViewById(R.id.event_title_text)
        val messageText: TextView = view.findViewById(R.id.event_message_text)
        val dateText: TextView = view.findViewById(R.id.event_date_text)
        val timeText: TextView = view.findViewById(R.id.event_time_text)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventViewHolder {
        val adapterLayout = LayoutInflater.from(parent.context)
            .inflate(R.layout.door_event, parent, false)
        return EventViewHolder(adapterLayout)
    }

    override fun onBindViewHolder(holder: EventViewHolder, position: Int) {
        val event = items[position]
        val display = DoorDisplayInfo.fromDoorState(context, event.state)
        holder.eventIcon.setImageDrawable(display.icon)
        holder.titleText.text = display.status
        holder.messageText.text = event.message
        holder.dateText.text = event.lastChangeTimeSeconds.toString()
        holder.timeText.text = event.lastChangeTimeSeconds.toString()
    }

    override fun getItemCount() = items.size
}