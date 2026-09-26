using System;
using System.Security.Cryptography;
using System.Text;
using System.Windows.Forms;

namespace DavidCompanion;

public static class PinSecurity
{
	public static bool HasPin()
	{
		SecuritySettings securitySettings = AppData.LoadSettings();
		if (securitySettings != null && !string.IsNullOrEmpty(securitySettings.PinSalt))
		{
			return !string.IsNullOrEmpty(securitySettings.PinHash);
		}
		return false;
	}

	public static bool EnsurePinCreated()
	{
		if (HasPin())
		{
			return true;
		}
		using SetPinForm setPinForm = new SetPinForm(changing: false);
		if (setPinForm.ShowDialog() != DialogResult.OK || string.IsNullOrEmpty(setPinForm.PinValue))
		{
			return false;
		}
		SetPin(setPinForm.PinValue);
		MessageBox.Show("Caregiver PIN saved. Keep this PIN somewhere safe.", "David", MessageBoxButtons.OK, MessageBoxIcon.Asterisk);
		return true;
	}

	public static void SetPin(string pin)
	{
		byte[] array = new byte[24];
		using (RandomNumberGenerator randomNumberGenerator = RandomNumberGenerator.Create())
		{
			randomNumberGenerator.GetBytes(array);
		}
		SecuritySettings securitySettings = AppData.LoadSettings() ?? new SecuritySettings();
		securitySettings.PinSalt = Convert.ToBase64String(array);
		securitySettings.PinHash = HashPin(pin, array);
		AppData.SaveSettings(securitySettings);
	}

	private static string HashPin(string pin, byte[] salt)
	{
		byte[] bytes = Encoding.UTF8.GetBytes(pin ?? "");
		byte[] array = new byte[salt.Length + bytes.Length];
		Buffer.BlockCopy(salt, 0, array, 0, salt.Length);
		Buffer.BlockCopy(bytes, 0, array, salt.Length, bytes.Length);
		using SHA256 sHA = SHA256.Create();
		return Convert.ToBase64String(sHA.ComputeHash(array));
	}

	public static bool Verify(string pin)
	{
		try
		{
			SecuritySettings securitySettings = AppData.LoadSettings();
			if (securitySettings == null || string.IsNullOrEmpty(securitySettings.PinSalt) || string.IsNullOrEmpty(securitySettings.PinHash))
			{
				return false;
			}
			byte[] salt = Convert.FromBase64String(securitySettings.PinSalt);
			return FixedEquals(HashPin(pin, salt), securitySettings.PinHash);
		}
		catch
		{
			return false;
		}
	}

	private static bool FixedEquals(string a, string b)
	{
		if (a == null || b == null || a.Length != b.Length)
		{
			return false;
		}
		int num = 0;
		for (int i = 0; i < a.Length; i++)
		{
			num |= a[i] ^ b[i];
		}
		return num == 0;
	}

	public static bool Prompt(string reason)
	{
		if (!HasPin() && !EnsurePinCreated())
		{
			return false;
		}
		for (int i = 0; i < 3; i++)
		{
			using (PinEntryForm pinEntryForm = new PinEntryForm(reason))
			{
				if (pinEntryForm.ShowDialog() != DialogResult.OK)
				{
					return false;
				}
				if (Verify(pinEntryForm.PinValue))
				{
					return true;
				}
			}
			MessageBox.Show("That PIN is incorrect.", "David", MessageBoxButtons.OK, MessageBoxIcon.Exclamation);
		}
		MessageBox.Show("Too many incorrect attempts. Try again later.", "David", MessageBoxButtons.OK, MessageBoxIcon.Exclamation);
		return false;
	}
}
