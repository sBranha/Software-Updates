using System;
using System.Diagnostics;
using System.Drawing;
using System.Linq;
using System.Windows.Forms;

namespace DavidCompanion;

public class ConnectedCareForm : Form
{
	private Label status = new Label();

	private Label person = new Label();

	private Label license = new Label();

	private Label sync = new Label();

	private TextBox code = new TextBox();

	public ConnectedCareForm()
	{
		Text = "David - Connected Care";
		StartPosition = FormStartPosition.CenterParent;
		Size = new Size(760, 590);
		FormBorderStyle = FormBorderStyle.FixedDialog;
		MaximizeBox = false;
		MinimizeBox = false;
		BackColor = Theme.Background;
		Font = new Font("Segoe UI", 10f);
		Icon = Program.TryLoadIcon();
		Label label = Ui.Label("Connected Care", 22, bold: true, Theme.Text);
		label.SetBounds(28, 22, 500, 44);
		Controls.Add(label);
		Label label2 = Ui.Label("Your Windows PC, Android phone, and caregiver website use the same person, reminders, DONE state, Chat, and HELP.", 10, bold: false, Theme.Muted);
		label2.SetBounds(30, 70, 680, 52);
		Controls.Add(label2);
		Panel panel = new Panel();
		panel.BackColor = Theme.Surface;
		panel.SetBounds(30, 135, 680, 150);
		Controls.Add(panel);
		status = Ui.Label("", 11, bold: true, Theme.Text);
		status.SetBounds(22, 18, 625, 28);
		panel.Controls.Add(status);
		person = Ui.Label("", 12, bold: true, Theme.Text);
		person.SetBounds(22, 54, 625, 30);
		panel.Controls.Add(person);
		license = Ui.Label("", 9, bold: false, Theme.Muted);
		license.SetBounds(22, 88, 625, 24);
		panel.Controls.Add(license);
		sync = Ui.Label("", 9, bold: false, Theme.Muted);
		sync.SetBounds(22, 116, 625, 24);
		panel.Controls.Add(sync);
		Label label3 = Ui.Label("6-DIGIT ADD DEVICE CODE  •  only needed if this PC is not already linked", 9, bold: true, Theme.Gold);
		label3.SetBounds(30, 310, 630, 25);
		Controls.Add(label3);
		code.SetBounds(30, 340, 310, 44);
		code.Font = new Font("Segoe UI", 18f, FontStyle.Bold);
		code.TextAlign = HorizontalAlignment.Center;
		code.MaxLength = 6;
		Controls.Add(code);
		Button button = Ui.Button("LINK / REPAIR THIS PC", Theme.Green, 9);
		button.SetBounds(360, 340, 350, 44);
		Controls.Add(button);
		Button button2 = Ui.Button("SYNC NOW", Color.FromArgb(73, 91, 117), 9);
		button2.SetBounds(30, 410, 210, 44);
		Controls.Add(button2);
		Button button3 = Ui.Button("OPEN CAREGIVER WEBSITE", Color.FromArgb(67, 135, 180), 9);
		button3.SetBounds(255, 410, 250, 44);
		Controls.Add(button3);
		Button button4 = Ui.Button("RESET LINK", Color.FromArgb(120, 72, 78), 9);
		button4.SetBounds(520, 410, 190, 44);
		Controls.Add(button4);
		Label label4 = Ui.Label("Existing licensed Windows 6.0.12 computers are migrated automatically when possible. A new code is only required if automatic linking cannot identify this PC.", 9, bold: false, Theme.Muted);
		label4.SetBounds(30, 477, 680, 48);
		Controls.Add(label4);
		button.Click += (object o, EventArgs e) =>
		{
			LinkOrRepair();
		};
		button2.Click += (object o, EventArgs e) =>
		{
			SyncNow();
		};
		button3.Click += (object o, EventArgs e) =>
		{
			OpenWeb();
		};
		button4.Click += (object o, EventArgs e) =>
		{
			ResetLink();
		};
		Shown += (object o, EventArgs e) =>
		{
			Cursor = Cursors.WaitCursor;
			CloudClient.EnsureSharedConnection();
			Cursor = Cursors.Default;
			RefreshView();
		};
		RefreshView();
	}

	private void RefreshView()
	{
		SecuritySettings securitySettings = AppData.LoadSettings() ?? new SecuritySettings();
		bool flag = CloudClient.IsConnected();
		status.Text = (flag ? "CONNECTED TO DAVID CONNECTED CARE" : "NOT LINKED TO A DAVID PERSON");
		status.ForeColor = (flag ? Theme.Green : Theme.Danger);
		string text = (string.IsNullOrWhiteSpace(securitySettings.PersonPreferredName) ? securitySettings.PersonFullName : securitySettings.PersonPreferredName);
		person.Text = (flag ? ("Person: " + (string.IsNullOrWhiteSpace(text) ? "David" : text)) : "Use the caregiver website → Person → Devices → Add Device.");
		license.Text = (string.IsNullOrWhiteSpace(securitySettings.LicensePublicId) ? "" : ("License: " + securitySettings.LicensePublicId + "  •  " + securitySettings.LicenseType));
		sync.Text = (DateTime.TryParse(securitySettings.CloudLastSyncUtc, out var result) ? ("Last shared sync: " + result.ToLocalTime().ToString("g")) : "Last shared sync: not yet");
	}

	private void LinkOrRepair()
	{
		string text = new string((code.Text ?? "").Where(char.IsDigit).ToArray());
		Cursor = Cursors.WaitCursor;
		bool flag = false;
		string text2 = "";
		if (text.Length == 6)
		{
			CloudResponse cloudResponse = CloudClient.ClaimSetupCode(text);
			flag = cloudResponse?.ok ?? false;
			text2 = ((cloudResponse == null) ? "David could not link this computer." : cloudResponse.error);
		}
		else
		{
			flag = CloudClient.EnsureSharedConnection();
			if (!flag)
			{
				text2 = "Automatic linking could not identify this PC. Generate a 6-digit code on the caregiver website under this person's Devices tab and enter it here.";
			}
		}
		Cursor = Cursors.Default;
		RefreshView();
		if (flag)
		{
			code.Text = "";
			MessageBox.Show("This Windows PC is linked to the same David person as the caregiver website and phone.", "David", MessageBoxButtons.OK, MessageBoxIcon.Asterisk);
			CloudSyncService.SyncAsync();
		}
		else
		{
			MessageBox.Show(text2, "David", MessageBoxButtons.OK, MessageBoxIcon.Exclamation);
		}
	}

	private void SyncNow()
	{
		Cursor = Cursors.WaitCursor;
		CloudResponse cloudResponse = CloudClient.SyncNow();
		Cursor = Cursors.Default;
		RefreshView();
		string text;
		if (cloudResponse != null && cloudResponse.ok)
		{
			text = "Shared reminders, completion state, Chat, and HELP are synchronized.";
		}
		else
		{
			text = ((cloudResponse == null) ? "Sync failed." : cloudResponse.error);
		}
		MessageBox.Show(text, "David");
	}

	private void OpenWeb()
	{
		try
		{
			Process.Start(new ProcessStartInfo
			{
				FileName = "https://david.forgegather.net",
				UseShellExecute = true
			});
		}
		catch
		{
		}
	}

	private void ResetLink()
	{
		if (MessageBox.Show("Reset this PC's Connected Care person link? Local reminders stay on this computer.", "David", MessageBoxButtons.YesNo, MessageBoxIcon.Exclamation) == DialogResult.Yes)
		{
			SecuritySettings securitySettings = AppData.LoadSettings() ?? new SecuritySettings();
			securitySettings.CloudConnectedPreference = "off";
			securitySettings.CloudApiMode = "";
			securitySettings.CloudPersonId = "";
			securitySettings.CloudDeviceId = "";
			securitySettings.CloudDeviceToken = "";
			securitySettings.CloudLastSyncUtc = "";
			securitySettings.LastChatNotifiedId = "";
			AppData.SaveSettings(securitySettings);
			RefreshView();
		}
	}
}
