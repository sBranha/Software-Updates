using System;
using System.Collections.Generic;
using System.Linq;
using System.Reflection;

namespace DavidCompanion;

public static class VoiceService
{
	public static List<VoiceChoice> GetVoices()
	{
		List<VoiceChoice> list = new List<VoiceChoice>();
		try
		{
			Type typeFromProgID = Type.GetTypeFromProgID("SAPI.SpVoice");
			if (typeFromProgID == null)
			{
				return list;
			}
			object target = Activator.CreateInstance(typeFromProgID);
			object obj = typeFromProgID.InvokeMember("GetVoices", BindingFlags.InvokeMethod, null, target, new object[2] { "", "" });
			Type type = obj.GetType();
			int num = Convert.ToInt32(type.InvokeMember("Count", BindingFlags.GetProperty, null, obj, null));
			for (int i = 0; i < num; i++)
			{
				object obj2 = type.InvokeMember("Item", BindingFlags.GetProperty, null, obj, new object[1] { i });
				string name = "Windows Voice " + (i + 1);
				try
				{
					name = Convert.ToString(obj2.GetType().InvokeMember("GetDescription", BindingFlags.InvokeMethod, null, obj2, new object[1] { 0 }));
				}
				catch
				{
				}
				list.Add(new VoiceChoice
				{
					Name = name,
					Token = obj2
				});
			}
		}
		catch
		{
		}
		return list;
	}

	public static object Speak(string text, string voiceName, int rate, int volume)
	{
		if (string.IsNullOrWhiteSpace(text))
		{
			return null;
		}
		try
		{
			Type typeFromProgID = Type.GetTypeFromProgID("SAPI.SpVoice");
			if (typeFromProgID == null)
			{
				return null;
			}
			object obj = Activator.CreateInstance(typeFromProgID);
			List<VoiceChoice> voices = GetVoices();
			VoiceChoice voiceChoice = voices.FirstOrDefault((VoiceChoice v) => string.Equals(v.Name, voiceName, StringComparison.OrdinalIgnoreCase));
			if (voiceChoice == null && string.IsNullOrWhiteSpace(voiceName))
			{
				voiceChoice = voices.FirstOrDefault((VoiceChoice v) => v.Name.IndexOf("David", StringComparison.OrdinalIgnoreCase) >= 0) ?? voices.FirstOrDefault((VoiceChoice v) => v.Name.IndexOf("Mark", StringComparison.OrdinalIgnoreCase) >= 0) ?? voices.FirstOrDefault();
			}
			if (voiceChoice != null)
			{
				try
				{
					typeFromProgID.InvokeMember("Voice", BindingFlags.SetProperty, null, obj, new object[1] { voiceChoice.Token });
				}
				catch
				{
				}
			}
			try
			{
				typeFromProgID.InvokeMember("Rate", BindingFlags.SetProperty, null, obj, new object[1] { Math.Max(-10, Math.Min(10, rate)) });
			}
			catch
			{
			}
			try
			{
				typeFromProgID.InvokeMember("Volume", BindingFlags.SetProperty, null, obj, new object[1] { Math.Max(0, Math.Min(100, volume)) });
			}
			catch
			{
			}
			typeFromProgID.InvokeMember("Speak", BindingFlags.InvokeMethod, null, obj, new object[2] { text, 1 });
			return obj;
		}
		catch
		{
			return null;
		}
	}

	public static void Stop(object speech)
	{
		if (speech == null)
		{
			return;
		}
		try
		{
			speech.GetType().InvokeMember("Speak", BindingFlags.InvokeMethod, null, speech, new object[2] { "", 3 });
		}
		catch
		{
		}
	}
}
