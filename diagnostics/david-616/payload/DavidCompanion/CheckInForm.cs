using System;
using System.Drawing;
using System.Media;
using System.Threading;
using System.Windows.Forms;

namespace DavidCompanion;

public class CheckInForm : Form
{
	private string key;

	private DateTime scheduled;

	private int graceMinutes;

	private object speech;

	private System.Windows.Forms.Timer timer = new System.Windows.Forms.Timer();

	private bool responded;

	private bool caregiverAlerted;

	private Label status = new Label();

	public CheckInForm(string stateKey, DateTime scheduledFor, int grace)
	{
		key = stateKey;
		scheduled = scheduledFor;
		graceMinutes = ((grace <= 0) ? 30 : grace);
		SecuritySettings s = AppData.LoadSettings() ?? new SecuritySettings();
		if (!string.Equals(s.WakeScreenPreference, "off", StringComparison.OrdinalIgnoreCase))
		{
			DisplayWake.Wake();
		}
		Text = "David - I'm Okay Check-In";
		StartPosition = FormStartPosition.CenterScreen;
		TopMost = true;
		BackColor = Color.FromArgb(7, 13, 24);
		Font = new Font("Segoe UI", 11f);
		Icon = Program.TryLoadIcon();
		if (!string.Equals(s.FullScreenPreference, "off", StringComparison.OrdinalIgnoreCase))
		{
			FormBorderStyle = FormBorderStyle.None;
			WindowState = FormWindowState.Maximized;
		}
		else
		{
			Size = new Size(900, 650);
			FormBorderStyle = FormBorderStyle.FixedDialog;
			MaximizeBox = false;
			MinimizeBox = false;
		}
		RoundedPanel card = Ui.Card(Color.FromArgb(20, 31, 48));
		card.Size = new Size(780, 500);
		card.Anchor = AnchorStyles.None;
		Controls.Add(card);
		card.Location = new Point((ClientSize.Width - card.Width) / 2, (ClientSize.Height - card.Height) / 2);
		SizeChanged += (object param0, EventArgs param1) =>
		{
			if (!card.IsDisposed)
			{
				card.Location = new Point((ClientSize.Width - card.Width) / 2, (ClientSize.Height - card.Height) / 2);
			}
		};
		string person = ((!string.IsNullOrWhiteSpace(s.PersonPreferredName)) ? s.PersonPreferredName : (string.IsNullOrWhiteSpace(s.PersonFullName) ? "David" : s.PersonFullName));
		Label label = Ui.Label("DAILY CHECK-IN", 10, bold: true, Theme.Blue);
		label.TextAlign = ContentAlignment.MiddleCenter;
		label.SetBounds(0, 38, card.Width, 30);
		card.Controls.Add(label);
		Label label2 = Ui.Label(person + ", are you okay?", 31, bold: true, Color.White);
		label2.TextAlign = ContentAlignment.MiddleCenter;
		label2.SetBounds(40, 90, 700, 70);
		card.Controls.Add(label2);
		Label label3 = Ui.Label("Press the green button so your caregiver knows you checked in.", 14, bold: false, Color.FromArgb(195, 210, 228));
		label3.TextAlign = ContentAlignment.MiddleCenter;
		label3.SetBounds(60, 170, 660, 60);
		card.Controls.Add(label3);
		Button button = Ui.Button("I'M OKAY", Theme.Green, 18);
		button.SetBounds(140, 260, 500, 92);
		card.Controls.Add(button);
		button.Click += (object param0, EventArgs param1) =>
		{
			RespondOkay(person);
		};
		Button button2 = Ui.Button("I NEED HELP", Theme.Danger, 11);
		button2.SetBounds(265, 370, 250, 58);
		card.Controls.Add(button2);
		button2.Click += (object param0, EventArgs param1) =>
		{
			CloudResponse cloudResponse = CloudClient.StartHelp();
			MessageBox.Show((cloudResponse != null && cloudResponse.ok) ? "Your caregivers were notified." : "David could not send the Help request right now.", "David");
		};
		status = Ui.Label("", 9, bold: true, Theme.Muted);
		status.TextAlign = ContentAlignment.MiddleCenter;
		status.SetBounds(80, 442, 620, 30);
		card.Controls.Add(status);
		FormClosing += (object sender, FormClosingEventArgs e) =>
		{
			if (!responded && e.CloseReason == CloseReason.UserClosing)
			{
				e.Cancel = true;
			}
		};
		Shown += (object param0, EventArgs param1) =>
		{
			AppData.SetState(key, "pending", scheduled.AddMinutes(graceMinutes));
			try
			{
				SystemSounds.Exclamation.Play();
			}
			catch
			{
			}
			speech = VoiceService.Speak(person + ", it's time for your check-in. Are you okay? Press the green I'm okay button.", s.VoiceName, s.VoiceRate, s.VoiceVolume);
			timer.Interval = 15000;
			timer.Tick += (object obj2, EventArgs e) =>
			{
				CheckGrace();
			};
			timer.Start();
			CheckGrace();
		};
		FormClosed += (object param0, FormClosedEventArgs param1) =>
		{
			timer.Stop();
			VoiceService.Stop(speech);
		};
	}

	private void CheckGrace()
	{
		TimeSpan timeSpan = scheduled.AddMinutes(graceMinutes) - DateTime.Now;
		if (timeSpan.TotalSeconds > 0.0)
		{
			status.Text = "Please check in within " + Math.Max(1, (int)Math.Ceiling(timeSpan.TotalMinutes)) + " minute" + ((Math.Ceiling(timeSpan.TotalMinutes) == 1.0) ? "" : "s") + ".";
		}
		else
		{
			if (caregiverAlerted)
			{
				return;
			}
			caregiverAlerted = true;
			status.Text = "Your caregiver is being alerted. You can still press I'M OKAY now.";
			AppData.SetState(key, "missed", null);
			AppData.AddSystemHistory("I'm Okay Check-In", "MISSED CHECK-IN", key, scheduled);
			if (!CloudClient.IsConnected())
			{
				return;
			}
			ThreadPool.QueueUserWorkItem((object param0) =>
			{
				try
				{
					CloudClient.ReplyChat("I did not respond to my scheduled I'm Okay check-in by " + DateTime.Now.ToString("h:mm tt") + ".", "", "checkin-missed");
				}
				catch
				{
				}
			});
		}
	}

	private void RespondOkay(string person)
	{
		responded = true;
		timer.Stop();
		VoiceService.Stop(speech);
		bool flag = caregiverAlerted || DateTime.Now > scheduled.AddMinutes(graceMinutes);
		AppData.SetState(key, "done", null);
		AppData.AddSystemHistory("I'm Okay Check-In", flag ? "Checked in OK after alert" : "Checked in OK", key, scheduled);
		if (flag && CloudClient.IsConnected())
		{
			ThreadPool.QueueUserWorkItem((object param0) =>
			{
				try
				{
					CloudClient.ReplyChat("I'm okay now. I responded to my check-in.", "", "checkin-ok");
				}
				catch
				{
				}
			});
		}
		MessageBox.Show("Thanks, " + person + ". You're checked in.", "David", MessageBoxButtons.OK, MessageBoxIcon.Asterisk);
		Close();
	}
}
