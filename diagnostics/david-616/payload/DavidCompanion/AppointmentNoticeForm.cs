using System;
using System.Drawing;
using System.Media;
using System.Windows.Forms;

namespace DavidCompanion;

public class AppointmentNoticeForm : Form
{
	private object speech;

	public AppointmentNoticeForm(Reminder r, DateTime due, string stage)
	{
		AppointmentNoticeForm appointmentNoticeForm = this;
		AppointmentNoticeForm appointmentNoticeForm2 = this;
		SecuritySettings s = AppData.LoadSettings() ?? new SecuritySettings();
		string person = ((!string.IsNullOrWhiteSpace(s.PersonPreferredName)) ? s.PersonPreferredName : (string.IsNullOrWhiteSpace(s.PersonFullName) ? "David" : s.PersonFullName));
		Text = "David - Appointment";
		StartPosition = FormStartPosition.CenterScreen;
		TopMost = true;
		Size = new Size(850, 610);
		FormBorderStyle = FormBorderStyle.FixedDialog;
		MaximizeBox = false;
		MinimizeBox = false;
		BackColor = Theme.Background;
		Font = new Font("Segoe UI", 10f);
		Icon = Program.TryLoadIcon();
		RoundedPanel roundedPanel = Ui.Card(Theme.Surface);
		roundedPanel.SetBounds(24, 24, 784, 470);
		Controls.Add(roundedPanel);
		Label label = Ui.Label("APPOINTMENT COUNTDOWN", 10, bold: true, Theme.Blue);
		label.SetBounds(30, 28, 400, 28);
		roundedPanel.Controls.Add(label);
		Label label2 = Ui.Label(r.Title ?? "Appointment", 27, bold: true, Theme.Text);
		label2.SetBounds(28, 68, 700, 58);
		roundedPanel.Controls.Add(label2);
		string text = (string.IsNullOrWhiteSpace(r.AppointmentWhere) ? "" : ("Where: " + r.AppointmentWhere + "\r\n"));
		string text2 = "When: " + due.ToString("dddd, MMMM d 'at' h:mm tt");
		Label label3 = Ui.Label(text + text2, 13, bold: false, Theme.Muted);
		label3.SetBounds(30, 135, 690, 70);
		roundedPanel.Controls.Add(label3);
		string stageText = ((stage == "tomorrow") ? "Your appointment is tomorrow." : ((stage == "prepare") ? "It’s time to start getting ready." : "It’s time to leave for your appointment."));
		Label label4 = Ui.Label(stageText, 20, bold: true, Theme.Text);
		label4.SetBounds(30, 230, 700, 55);
		roundedPanel.Controls.Add(label4);
		if (!string.IsNullOrWhiteSpace(r.Message))
		{
			Label label5 = Ui.Label(r.Message, 11, bold: false, Theme.Muted);
			label5.SetBounds(30, 292, 690, 55);
			roundedPanel.Controls.Add(label5);
		}
		Button button = Ui.Button("OK", Theme.Green, 12);
		button.SetBounds(250, 375, 285, 62);
		roundedPanel.Controls.Add(button);
		button.Click += (object param0, EventArgs param1) =>
		{
			appointmentNoticeForm2.Close();
		};
		Shown += (object param0, EventArgs param1) =>
		{
			try
			{
				SystemSounds.Exclamation.Play();
			}
			catch
			{
			}
			string text3 = person + ", " + stageText + " " + (r.Title ?? "Your appointment") + " is at " + due.ToString("h:mm tt") + (string.IsNullOrWhiteSpace(r.AppointmentWhere) ? "." : (" at " + r.AppointmentWhere + "."));
			appointmentNoticeForm.speech = VoiceService.Speak(text3, s.VoiceName, s.VoiceRate, s.VoiceVolume);
		};
		FormClosed += (object param0, FormClosedEventArgs param1) =>
		{
			VoiceService.Stop(appointmentNoticeForm2.speech);
		};
	}
}
