using System;
using System.Collections.Generic;
using System.Drawing;
using System.IO;
using System.Linq;
using System.Windows.Forms;

namespace DavidCompanion;

public class HealthCheckForm : Form
{
	private FlowLayoutPanel list = new FlowLayoutPanel();

	private Label overall = new Label();

	public HealthCheckForm()
	{
		Text = "David 5.9 - Health Check";
		StartPosition = FormStartPosition.CenterParent;
		Size = new Size(760, 650);
		BackColor = Theme.Background;
		Font = new Font("Segoe UI", 10f);
		Icon = Program.TryLoadIcon();
		Label label = Ui.Label("David Health Check", 22, bold: true, Theme.Text);
		label.SetBounds(26, 20, 500, 42);
		Controls.Add(label);
		Label label2 = Ui.Label("Check the computer, reminders, startup, Connected Care, and HELP notification path before you leave.", 10, bold: false, Theme.Muted);
		label2.SetBounds(29, 62, 680, 48);
		Controls.Add(label2);
		overall = Ui.Label("Checking…", 13, bold: true, Theme.Text);
		overall.SetBounds(29, 115, 650, 34);
		Controls.Add(overall);
		list.SetBounds(26, 158, 690, 300);
		list.FlowDirection = FlowDirection.TopDown;
		list.WrapContents = false;
		list.AutoScroll = true;
		list.BackColor = Theme.Background;
		Controls.Add(list);
		Button button = Ui.Button("RUN CHECK AGAIN", Theme.Blue, 9);
		button.SetBounds(26, 485, 190, 44);
		Controls.Add(button);
		EventHandler value = (object param0, EventArgs param1) =>
		{
			RunChecks();
		};
		button.Click += value;
		Button button2 = Ui.Button("TEST CAREGIVER NOTIFICATION", Theme.Gold, 9);
		button2.SetBounds(232, 485, 270, 44);
		Controls.Add(button2);
		button2.Click += (object param0, EventArgs param1) =>
		{
			if (!CloudClient.IsConnected())
			{
				MessageBox.Show("Connect David to caregivers first.", "David");
			}
			else if (MessageBox.Show("Send a harmless HELP test notification to the connected caregivers? No real Help request will be created.", "David", MessageBoxButtons.YesNo) == DialogResult.Yes)
			{
				Cursor = Cursors.WaitCursor;
				CloudResponse cloudResponse = CloudClient.TestHelpNotification();
				Cursor = Cursors.Default;
				MessageBox.Show((cloudResponse != null && cloudResponse.ok) ? "Test notification sent. Ask the caregiver to confirm it arrived." : ((cloudResponse == null) ? "Test failed." : cloudResponse.error), "David");
				RunChecks();
			}
		};
		Button button3 = Ui.Button("BACK UP LOCAL DATA", Color.FromArgb(73, 91, 117), 9);
		button3.SetBounds(518, 485, 198, 44);
		Controls.Add(button3);
		button3.Click += (object param0, EventArgs param1) =>
		{
			try
			{
				string text = AppData.Backup();
				MessageBox.Show("Backup created in:\r\n" + text, "David");
			}
			catch (Exception ex)
			{
				MessageBox.Show(ex.Message, "David");
			}
		};
		Button button4 = Ui.Button("OPEN HELP GUIDE", Color.FromArgb(73, 91, 117), 9);
		button4.SetBounds(518, 540, 198, 42);
		Controls.Add(button4);
		button4.Click += (object param0, EventArgs param1) =>
		{
			using HelpGuideForm helpGuideForm = new HelpGuideForm();
			helpGuideForm.ShowDialog(this);
		};
		Shown += (object param0, EventArgs param1) =>
		{
			RunChecks();
		};
	}

	private void AddCheck(string name, bool ok, string detail)
	{
		RoundedPanel roundedPanel = Ui.Card(ok ? Color.FromArgb(20, 55, 44) : Color.FromArgb(63, 38, 43));
		roundedPanel.Size = new Size(650, 54);
		Label label = Ui.Label((ok ? "✓  " : "!  ") + name, 10, bold: true, ok ? Theme.Green : Theme.Danger);
		label.SetBounds(14, 8, 260, 22);
		roundedPanel.Controls.Add(label);
		Label label2 = Ui.Label(detail, 9, bold: false, Theme.Muted);
		label2.SetBounds(280, 8, 350, 35);
		roundedPanel.Controls.Add(label2);
		list.Controls.Add(roundedPanel);
	}

	private void RunChecks()
	{
		list.Controls.Clear();
		int num = 0;
		bool flag = Directory.Exists(AppData.Root);
		AddCheck("Local data", flag, flag ? "Data folder is available." : "Data folder is missing.");
		if (!flag)
		{
			num++;
		}
		bool flag2 = AppData.ValidateData(out var detail);
		AddCheck("Data integrity", flag2, detail);
		if (!flag2)
		{
			num++;
		}
		bool flag3 = !string.Equals((AppData.LoadSettings() ?? new SecuritySettings()).AutoBackupEnabled, "off", StringComparison.OrdinalIgnoreCase);
		AddCheck("Automatic backup", flag3, flag3 ? "Daily local backup is enabled." : "Automatic daily backup is turned off.");
		bool flag4 = LicenseService.IsLocallyActive();
		SecuritySettings securitySettings = AppData.LoadSettings() ?? new SecuritySettings();
		AddCheck("David license", flag4, flag4 ? (LicenseService.TypeLabel(securitySettings) + (string.Equals(securitySettings.LicenseNeverExpires, "yes", StringComparison.OrdinalIgnoreCase) ? " — never expires" : "")) : "Activation is required.");
		if (!flag4)
		{
			num++;
		}
		bool flag5 = PinSecurity.HasPin();
		AddCheck("Caregiver PIN", flag5, flag5 ? "Protected controls are enabled." : "Create a caregiver PIN.");
		if (!flag5)
		{
			num++;
		}
		bool flag6 = Program.IsAutoStartEnabled();
		AddCheck("Automatic startup", flag6, flag6 ? "David starts when this Windows user signs in." : "Startup is turned off.");
		if (!flag6)
		{
			num++;
		}
		int num2 = (AppData.LoadReminders().Items ?? new List<Reminder>()).Count((Reminder r) => r != null && !r.Deleted && r.Enabled);
		AddCheck("Reminders", ok: true, num2 + " active reminder" + ((num2 == 1) ? "" : "s") + " stored locally.");
		bool flag7 = CloudClient.IsConnected();
		string detail2 = (flag7 ? "Connected Care configured." : "Local mode only.");
		if (flag7)
		{
			CloudResponse cloudResponse = CloudClient.SyncNow();
			flag7 = cloudResponse?.ok ?? false;
			if (flag7)
			{
				detail2 = "Server sync completed.";
			}
			else
			{
				detail2 = ((cloudResponse == null) ? "Server sync failed." : cloudResponse.error);
			}
		}
		AddCheck("Connected Care", flag7, detail2);
		if (!flag7)
		{
			num++;
		}
		SecuritySettings securitySettings2 = AppData.LoadSettings() ?? new SecuritySettings();
		AddCheck("Last sync", !string.IsNullOrWhiteSpace(securitySettings2.CloudLastSyncUtc), string.IsNullOrWhiteSpace(securitySettings2.CloudLastSyncUtc) ? "No successful sync recorded." : securitySettings2.CloudLastSyncUtc);
		overall.Text = ((num == 0) ? "READY — David passed the core computer checks" : ("NEEDS ATTENTION — " + num + " core check" + ((num == 1) ? "" : "s") + " failed"));
		overall.ForeColor = ((num == 0) ? Theme.Green : Theme.Gold);
	}
}
