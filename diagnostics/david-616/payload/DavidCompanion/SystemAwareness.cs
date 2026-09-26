using System;
using System.Net.NetworkInformation;
using System.Runtime.InteropServices;
using System.Windows.Forms;

namespace DavidCompanion;

public static class SystemAwareness
{
	private struct LASTINPUTINFO
	{
		public uint cbSize;

		public uint dwTime;
	}

	[DllImport("user32.dll")]
	private static extern bool GetLastInputInfo(ref LASTINPUTINFO plii);

	public static bool InternetAvailable()
	{
		try
		{
			return NetworkInterface.GetIsNetworkAvailable();
		}
		catch
		{
			return false;
		}
	}

	public static double IdleMinutes()
	{
		try
		{
			LASTINPUTINFO plii = new LASTINPUTINFO
			{
				cbSize = (uint)Marshal.SizeOf(typeof(LASTINPUTINFO))
			};
			if (!GetLastInputInfo(ref plii))
			{
				return 0.0;
			}
			return (double)((uint)Environment.TickCount - plii.dwTime) / 60000.0;
		}
		catch
		{
			return 0.0;
		}
	}

	public static int BatteryPercent()
	{
		try
		{
			float batteryLifePercent = SystemInformation.PowerStatus.BatteryLifePercent;
			if (batteryLifePercent < 0f || batteryLifePercent > 1.01f)
			{
				return -1;
			}
			return Math.Max(0, Math.Min(100, (int)Math.Round(batteryLifePercent * 100f)));
		}
		catch
		{
			return -1;
		}
	}

	public static bool HasBattery()
	{
		try
		{
			return (SystemInformation.PowerStatus.BatteryChargeStatus & BatteryChargeStatus.NoSystemBattery) == 0;
		}
		catch
		{
			return false;
		}
	}

	public static bool LowBattery()
	{
		try
		{
			int num = BatteryPercent();
			return HasBattery() && SystemInformation.PowerStatus.PowerLineStatus == PowerLineStatus.Offline && num >= 0 && num <= 20;
		}
		catch
		{
			return false;
		}
	}

	public static string PowerSummary()
	{
		try
		{
			if (!HasBattery())
			{
				return "AC power / desktop computer";
			}
			int num = BatteryPercent();
			return ((SystemInformation.PowerStatus.PowerLineStatus == PowerLineStatus.Online) ? "Plugged in" : "On battery") + ((num >= 0) ? (" • " + num + "%") : "");
		}
		catch
		{
			return "Power status unavailable";
		}
	}

	public static double LastSyncAgeMinutes(SecuritySettings s)
	{
		if (s == null || !DateTime.TryParse(s.CloudLastSyncUtc, out var result))
		{
			return double.PositiveInfinity;
		}
		return Math.Max(0.0, (DateTime.UtcNow - result.ToUniversalTime()).TotalMinutes);
	}

	public static string ConnectedCareSummary(SecuritySettings s)
	{
		if (!CloudClient.IsConnected())
		{
			return "Local mode — Connected Care is not enabled";
		}
		if (!InternetAvailable())
		{
			return "Connected Care configured, but the internet is offline";
		}
		double num = LastSyncAgeMinutes(s);
		if (double.IsInfinity(num))
		{
			return "Connected Care configured — no successful sync yet";
		}
		if (num > 10.0)
		{
			return "Connected Care delayed — last sync " + FriendlyAge(num) + " ago";
		}
		return "Connected Care online — last sync " + FriendlyAge(num) + " ago";
	}

	public static string FriendlyAge(double minutes)
	{
		if (double.IsInfinity(minutes))
		{
			return "not yet";
		}
		if (minutes < 1.0)
		{
			return "less than a minute";
		}
		if (minutes < 60.0)
		{
			return (int)Math.Round(minutes) + " min";
		}
		return Math.Max(1, (int)Math.Round(minutes / 60.0)) + " hr";
	}

	private static TimeSpan ParseTime(string value, int fallbackHour)
	{
		if (!TimeSpan.TryParse(value, out var result))
		{
			return TimeSpan.FromHours(fallbackHour);
		}
		return result;
	}

	public static bool InWatchWindow(SecuritySettings s, DateTime now, out DateTime windowStart)
	{
		TimeSpan timeSpan = ParseTime(s?.InactivityWatchStartTime, 8);
		TimeSpan timeSpan2 = ParseTime(s?.InactivityWatchEndTime, 22);
		DateTime dateTime = now.Date.Add(timeSpan);
		DateTime dateTime2 = now.Date.Add(timeSpan2);
		if (timeSpan <= timeSpan2)
		{
			windowStart = dateTime;
			if (now >= dateTime)
			{
				return now <= dateTime2;
			}
			return false;
		}
		if (now.TimeOfDay >= timeSpan)
		{
			windowStart = dateTime;
			return true;
		}
		if (now.TimeOfDay <= timeSpan2)
		{
			windowStart = now.Date.AddDays(-1.0).Add(timeSpan);
			return true;
		}
		windowStart = dateTime;
		return false;
	}

	public static double EffectiveWatchIdleHours(SecuritySettings s, DateTime now)
	{
		if (!InWatchWindow(s, now, out var windowStart))
		{
			return 0.0;
		}
		double val = Math.Max(0.0, (now - windowStart).TotalHours);
		return Math.Min(IdleMinutes() / 60.0, val);
	}

	public static string HomeStatus(SecuritySettings s)
	{
		if (LowBattery())
		{
			return "PLUG IN DAVID";
		}
		if (!InternetAvailable())
		{
			return "INTERNET OFFLINE";
		}
		if (CloudClient.IsConnected() && LastSyncAgeMinutes(s) > 10.0)
		{
			return "CONNECTED CARE DELAYED";
		}
		return "TODAY STATUS • SYSTEM OK";
	}
}
