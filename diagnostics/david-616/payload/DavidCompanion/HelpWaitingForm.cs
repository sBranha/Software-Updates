using System;
using System.Drawing;
using System.Threading;
using System.Windows.Forms;

namespace DavidCompanion;

public class HelpWaitingForm : Form
{
	private Label title = new Label();

	private Label detail = new Label();

	private System.Windows.Forms.Timer timer = new System.Windows.Forms.Timer();

	private bool polling;

	public HelpWaitingForm(CloudHelp help)
	{
		HelpWaitingForm helpWaitingForm = this;
		Text = "David - Help Request";
		StartPosition = FormStartPosition.CenterScreen;
		Size = new Size(700, 430);
		FormBorderStyle = FormBorderStyle.FixedDialog;
		MaximizeBox = false;
		MinimizeBox = false;
		BackColor = Theme.Background;
		Font = new Font("Segoe UI", 10f);
		Icon = Program.TryLoadIcon();
		TopMost = true;
		Panel panel = new Panel();
		panel.BackColor = Color.FromArgb(108, 35, 48);
		panel.SetBounds(25, 25, 635, 310);
		Controls.Add(panel);
		Label label = Ui.Label("HELP REQUEST SENT", 11, bold: true, Color.FromArgb(255, 208, 214));
		label.SetBounds(28, 25, 560, 30);
		panel.Controls.Add(label);
		title = Ui.Label("Your caregivers have been notified.", 22, bold: true, Color.White);
		title.SetBounds(28, 72, 575, 70);
		panel.Controls.Add(title);
		detail = Ui.Label("Someone will respond soon. You can leave this screen open.", 12, bold: false, Color.FromArgb(248, 224, 228));
		detail.SetBounds(31, 150, 560, 65);
		panel.Controls.Add(detail);
		Button button = Ui.Button("CLOSE", Color.FromArgb(91, 105, 125), 10);
		button.SetBounds(435, 242, 165, 45);
		panel.Controls.Add(button);
		EventHandler value = (object param0, EventArgs param1) =>
		{
			helpWaitingForm.Close();
		};
		button.Click += value;
		timer.Interval = 5000;
		timer.Tick += (object param0, EventArgs param1) =>
		{
			helpWaitingForm.Poll();
		};
		timer.Start();
		Shown += (object param0, EventArgs param1) =>
		{
			Apply(help);
			Poll();
		};
		FormClosed += (object param0, FormClosedEventArgs param1) =>
		{
			helpWaitingForm.timer.Stop();
		};
	}

	private void Poll()
	{
		if (polling)
		{
			return;
		}
		polling = true;
		ThreadPool.QueueUserWorkItem((object param0) =>
		{
			CloudResponse r = CloudClient.HelpStatus();
			try
			{
				BeginInvoke((MethodInvoker)(() =>
				{
					polling = false;
					if (r != null && r.ok)
					{
						Apply(r.help);
					}
				}));
			}
			catch
			{
				polling = false;
			}
		});
	}

	private void Apply(CloudHelp h)
	{
		if (h == null)
		{
			title.Text = "Your help request is no longer active.";
			detail.Text = "A caregiver may have resolved it.";
		}
		else if (string.Equals(h.status, "claimed", StringComparison.OrdinalIgnoreCase))
		{
			string text = (string.IsNullOrWhiteSpace(h.claimedName) ? "A caregiver" : h.claimedName);
			title.Text = text + " is helping you.";
			detail.Text = "Your caregiver received the Help request and is responding.";
		}
		else
		{
			title.Text = "Your caregivers have been notified.";
			detail.Text = "Someone will respond soon. David will keep notifying caregivers until somebody responds.";
		}
	}
}
