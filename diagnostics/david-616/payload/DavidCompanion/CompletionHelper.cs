using System;
using System.Windows.Forms;

namespace DavidCompanion;

public static class CompletionHelper
{
	public static bool NeedsConfirmation(Reminder r)
	{
		if (r != null)
		{
			if (!r.ConfirmDone && !string.Equals(r.Category, "Medication", StringComparison.OrdinalIgnoreCase))
			{
				return string.Equals(r.Importance, "Critical", StringComparison.OrdinalIgnoreCase);
			}
			return true;
		}
		return false;
	}

	public static bool Confirm(IWin32Window owner, Reminder r)
	{
		if (!NeedsConfirmation(r))
		{
			return true;
		}
		string text = (string.Equals(r.Category, "Medication", StringComparison.OrdinalIgnoreCase) ? "Did you take your medicine?" : "Did you complete this important task?");
		return MessageBox.Show(owner, text + "\r\n\r\n" + ((r == null) ? "" : r.Title), "David", MessageBoxButtons.YesNo, MessageBoxIcon.Question) == DialogResult.Yes;
	}
}
