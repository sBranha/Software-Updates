using System;
using System.Drawing;

namespace DavidCompanion;

public static class Theme
{
	public static Color Background
	{
		get
		{
			if (!IsNight)
			{
				return Color.FromArgb(235, 241, 247);
			}
			return Color.FromArgb(9, 15, 27);
		}
	}

	public static Color Surface
	{
		get
		{
			if (!IsNight)
			{
				return Color.FromArgb(251, 253, 255);
			}
			return Color.FromArgb(20, 31, 48);
		}
	}

	public static Color Surface2
	{
		get
		{
			if (!IsNight)
			{
				return Color.FromArgb(243, 247, 251);
			}
			return Color.FromArgb(27, 41, 61);
		}
	}

	public static Color Surface3
	{
		get
		{
			if (!IsNight)
			{
				return Color.FromArgb(233, 240, 247);
			}
			return Color.FromArgb(34, 51, 75);
		}
	}

	public static Color Text
	{
		get
		{
			if (!IsNight)
			{
				return Color.FromArgb(20, 31, 45);
			}
			return Color.FromArgb(244, 248, 252);
		}
	}

	public static Color Muted
	{
		get
		{
			if (!IsNight)
			{
				return Color.FromArgb(91, 107, 126);
			}
			return Color.FromArgb(166, 183, 204);
		}
	}

	public static Color Border
	{
		get
		{
			if (!IsNight)
			{
				return Color.FromArgb(210, 220, 231);
			}
			return Color.FromArgb(49, 67, 91);
		}
	}

	public static Color Navy => Color.FromArgb(16, 35, 61);

	public static Color Navy2 => Color.FromArgb(29, 58, 91);

	public static Color Blue => Color.FromArgb(54, 143, 202);

	public static Color Gold => Color.FromArgb(239, 165, 58);

	public static Color Green => Color.FromArgb(47, 177, 119);

	public static Color Danger => Color.FromArgb(226, 76, 84);

	public static bool IsNight
	{
		get
		{
			int hour = DateTime.Now.Hour;
			if (hour >= 6)
			{
				return hour >= 18;
			}
			return true;
		}
	}

	public static Color Category(string category)
	{
		return (category ?? "").ToLowerInvariant() switch
		{
			"medication" => Color.FromArgb(123, 109, 226), 
			"hygiene" => Color.FromArgb(55, 164, 204), 
			"pets" => Color.FromArgb(47, 174, 119), 
			"meals" => Color.FromArgb(234, 139, 62), 
			"appointment" => Color.FromArgb(226, 171, 52), 
			"chore" => Color.FromArgb(89, 133, 184), 
			"bedtime" => Color.FromArgb(86, 102, 184), 
			_ => Color.FromArgb(113, 129, 151), 
		};
	}

	public static string CategoryBadge(string category)
	{
		return (category ?? "").ToLowerInvariant() switch
		{
			"medication" => "Rx", 
			"hygiene" => "H2O", 
			"pets" => "PET", 
			"meals" => "FOOD", 
			"appointment" => "CAL", 
			"chore" => "TASK", 
			"bedtime" => "Zz", 
			_ => "DO", 
		};
	}
}
