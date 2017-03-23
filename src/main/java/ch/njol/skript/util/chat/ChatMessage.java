/*
 *   This file is part of Skript.
 *
 *  Skript is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Skript is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with Skript.  If not, see <http://www.gnu.org/licenses/>.
 * 
 * 
 * Copyright 2011-2017 Peter Güttinger and contributors
 * 
 */

package ch.njol.skript.util.chat;

import java.io.NotSerializableException;
import java.io.StreamCorruptedException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.soap.Text;

import org.bukkit.event.Event;
import org.eclipse.jdt.annotation.Nullable;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;

import ch.njol.skript.classes.ClassInfo;
import ch.njol.skript.classes.Converter;
import ch.njol.skript.classes.Serializer;
import ch.njol.skript.lang.Debuggable;
import ch.njol.skript.localization.Language;
import ch.njol.skript.localization.LanguageChangeListener;
import ch.njol.skript.registrations.Classes;
import ch.njol.skript.registrations.Converters;
import ch.njol.skript.util.Color;
import ch.njol.yggdrasil.Fields;

/**
 * Represents a chat message in JSON format.
 */
public class ChatMessage implements Debuggable {
	
	/**
	 * Chat codes, see {@link ChatCode}.
	 */
	static final Map<String,ChatCode> codes = new HashMap<>();
	
	/**
	 * Instance of GSON we use for serialization.
	 */
	static final Gson gson = new Gson();
	
	static {
		// When language changes or server is loaded loop through all chatcodes
		Language.addListener(new LanguageChangeListener() {
			
			@Override
			public void onLanguageChange() {
				codes.clear();
				
				for (ChatCode code : ChatCode.values()) {
					if (code.colorCode != null) { // Color code!
						for (String name : Language.getList(Color.LANGUAGE_NODE + "." + code.colorLangName)) {
							codes.put(name, code);
						}
					} else { // Not color code
						for (String name : Language.getList("chat styles." + code.name())) {
							codes.put(name, code);
						}
					}
				}
			}
		});
		
		// This one converts strings to chat messages if requested
		Converters.registerConverter(String.class, ChatMessage.class, new Converter<String, ChatMessage>() {

			@Override
			@Nullable
			public ChatMessage convert(String f) {
				return new ChatMessage(f);
			}
			
		});
	}
	
	/**
	 * Component list - this is just serialization mapper class.
	 */
	private static class ComponentList {
		
		public ComponentList(List<MessageComponent> components) {
			this.extra = components;
		}

		/**
		 * DO NOT USE!
		 */
		@SuppressWarnings("unused")
		@Deprecated
		public String text = "";
		
		/**
		 * Actual message data.
		 */
		@SuppressWarnings("unused")
		@Nullable
		public List<MessageComponent> extra;
	}
	
	private static String parse(String msg) {
		char[] chars = msg.toCharArray();
		
		List<MessageComponent> components = new ArrayList<>();
		MessageComponent current = new MessageComponent();
		components.add(current);
		StringBuilder curStr = new StringBuilder();
		
		char previous = 0;
		int tagStart = 0;
		for (int i = 0; i < chars.length; i++) {
			char c = chars[i];
			if (c == '\\' || previous == '\\') // \ serves as escape character
				continue;
			previous = c;
			curStr.append(c); // Append this char to curStr
			
			if (c == '<') {
				tagStart = i;
			} else if (c == '>') {
				String tag = msg.substring(tagStart + 1, i);
				
				String name;
				String param = "";
				if (tag.contains(":")) {
					String[] split = tag.split(":");
					name = split[0];
					param = split[1];
				} else {
					name = tag;
				}
				
				ChatCode code = codes.get(name);
				if (code.nextComponent()) { // Next chat component, someone asked for reset
					String text = curStr.toString();
					assert text != null;
					current.text = text;
					current = new MessageComponent();
					components.add(current);
				}
				
				assert param != null;
				if (code.colorCode != null) // Just update color code
					current.color = code.colorCode;
				else
					code.updateComponent(current, param); // Actually update the component...
			}
		}
		String text = curStr.toString();
		assert text != null;
		current.text = text;
		
		ComponentList componentList = new ComponentList(components);
		String json = gson.toJson(componentList);
		assert json != null;
		return json;
	}
	
	public static ChatMessage fromJson(String json) {
		ChatMessage message = new ChatMessage();
		message.json = json;
		return message;
	}
	
	String json;
	
	@SuppressWarnings("null")
	ChatMessage() {
		// when using this, remember to actually put something to json field
	}
	
	public ChatMessage(String text) {
		json = parse(text);
	}
	
	public String getJson() {
		return json;
	}

	@Override
	public String toString(@Nullable Event e, boolean debug) {
		return json;
	}
	
	@Override
	public String toString() {
		return toString(null, false);
	}
}