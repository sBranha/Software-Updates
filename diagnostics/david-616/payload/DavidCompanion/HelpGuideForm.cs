using System;
using System.Diagnostics;
using System.Drawing;
using System.Windows.Forms;

namespace DavidCompanion;

public class HelpGuideForm : Form
{
	public HelpGuideForm()
	{
		Text = "David 6.1.6 - Help Guide";
		StartPosition = FormStartPosition.CenterParent;
		Size = new Size(760, 680);
		BackColor = Theme.Background;
		Font = new Font("Segoe UI", 10f);
		Icon = Program.TryLoadIcon();
		Label label = Ui.Label("David Quick Help", 22, bold: true, Theme.Text);
		label.SetBounds(25, 18, 600, 44);
		Controls.Add(label);
		RichTextBox richTextBox = new RichTextBox();
		richTextBox.SetBounds(26, 75, 690, 505);
		richTextBox.ReadOnly = true;
		richTextBox.BackColor = Theme.Surface;
		richTextBox.ForeColor = Theme.Text;
		richTextBox.BorderStyle = BorderStyle.FixedSingle;
		richTextBox.Font = new Font("Segoe UI", 11f);
		richTextBox.Text = "REMINDERS\r\nOpen CAREGIVER → ADD to create or change reminders. Important, Critical, and Medication reminders repeat more aggressively. For Medication reminders, the Medication / Appointment tab can store the exact dose and medicine instructions. David displays those words but never calculates a catch-up or double dose.\r\n\r\nAPPOINTMENTS\r\nAppointment reminders can include a location plus caregiver-set get-ready and leave times. David can give a tomorrow notice, a get-ready notice, and a time-to-leave notice before the normal appointment reminder.\r\n\r\nCAREGIVER HOME MESSAGE\r\nThe newest caregiver chat message stays on David's home screen until David taps it and presses OKAY. He can also have David read the message aloud or open Chat.\r\n\r\nDAILY SUPPORT\r\nUse WHAT DO I NEED TO DO? on the home screen for simple next-step guidance. Morning and Evening Routine buttons group reminders whose Advanced → Routine name matches the caregiver settings. Caregiver Settings → Daily Support controls automatic routine prompts and the scheduled I’M OKAY check-in.\r\n\r\nI’M OKAY CHECK-IN\r\nWhen enabled, David asks for a daily check-in. If there is no response before the grace period ends, David records the missed check-in and sends a Connected Care chat alert when connected.\r\n\r\nCHAT\r\nUse CHAT for normal messages with connected caregivers. Quick YES, NO, I’M OKAY and CALL ME replies are linked to the caregiver message they answer so the caregiver can see that it was replied to.\r\n\r\nHELP\r\nUse the red HELP button for an urgent situation. The Health Check has a harmless test that does not create a real Help request.\r\n\r\nSMART QUIETING & MISSED SUMMARY\r\nSmart Quieting prevents an old non-critical reminder from suddenly popping up long after its time. Instead, David saves it for one missed-items summary. Important, Critical, Repeat Until Done, and Medication reminders are never silently moved into the quiet summary. The home screen can show REVIEW MISSED ITEMS, and the evening summary can be scheduled under Caregiver Settings → Summaries.\r\n\r\nCAREGIVER DAILY SUMMARY\r\nWhen enabled, David sends one Connected Care message at the caregiver-selected time with scheduled, completed, missed/waiting, medication, and I’M OKAY check-in status. It sends at most once per day and retries after a temporary connection failure.\r\n\r\nTODAY STATUS & CONNECTION HEALTH\r\nCaregivers open CAREGIVER → COMPUTER & SAFETY → TODAY STATUS. It shows power, internet, Connected Care sync health, last keyboard/mouse activity, today’s completion, medication completion, and I’M OKAY status. These computer-management details are intentionally kept off David's daily home screen.\r\n\r\nCAREGIVER ACTIVITY AWARENESS\r\nCaregiver Settings → Awareness can optionally send a Connected Care notice after unusually long keyboard/mouse inactivity during a caregiver-selected daytime watch window. It is OFF by default and is only an activity signal, not proof of an emergency. David can also send one short active-again update after the computer is used again.\r\n\r\nQUIET HOURS & AWAY MODE\r\nCaregiver Settings → Quiet & Away can hold ordinary reminders during sleep or temporarily while David is away. Quiet Hours release ordinary reminders after the quiet period using the same single reminder window. Away Mode saves ordinary reminders for one return-home review instead of immediately playing them all. Medication, Appointment, Important, Critical, and Repeat Until Done reminders always break through. Caregivers use CAREGIVER → COMPUTER & SAFETY → QUIET / AWAY to see the current mode, review held items, or end Away Mode.\r\n\r\nCAREGIVER INSIGHTS\r\nOpen CAREGIVER and choose INSIGHTS to review the last 7, 14, or 30 days. The screen shows reminder completion, medication completion, I’M OKAY check-in consistency, and the reminders most often left incomplete. These are factual activity summaries, not a medical assessment. Use TODAY DETAIL for the existing one-day report.\r\n\r\nWEEK AHEAD & ORIENTATION\r\nUse WHAT'S COMING UP on the home screen for a seven-day agenda with TODAY and TOMORROW clearly labeled. Press READ OVERVIEW TO ME for a short spoken summary. Caregiver Settings → Orientation can optionally speak one morning overview each day without opening another popup. The overview respects Quiet Hours and Away Mode and does not read medicine instructions or caregiver notes.\r\n\r\nLICENSE\r\nDavid 6.0 requires a valid software activation. Lifetime, Complimentary Lifetime, and Organization Lifetime licenses never expire. Activation is tied to this Windows computer and the token is protected with Windows encryption. Caregivers can review the license under CAREGIVER → COMPUTER & SAFETY → LICENSE.\r\n\r\nSERVICE CONTROL\r\nThe David Administrator can place Connected Care in Service On, Support Needed, or Service Suspended mode. Service Suspended pauses cloud sync, Chat, HELP delivery, new Connected Care setup, new activation/validation, and update checks. Local reminders, history, and saved daily-care information on this computer remain available. A normal server outage by itself does not automatically suspend David.\r\n\r\nUPDATES\r\nCaregivers use CAREGIVER → CHECK UPDATES. David checks the official public GitHub Software-Updates repository for the current Windows release, verifies the published SHA-256, creates a fresh local backup, and launches the prebuilt Windows Setup.exe. Website updates remain on the website and are separate from software downloads. Automatic update checks run quietly in the background. When a newer version is published, David shows a local caregiver notice and sends one Connected Care update notice to the caregiver linked to that computer. Each release is sent only once so caregivers are not spammed. Future Mac builds use the same linked-caregiver notification pattern.\r\n\r\nCONNECTED CARE\r\nFor a new computer, create a six-digit setup code at david.forgegather.net/setup.html. The normal 5.9 setup does not require the private server setup key.\r\n\r\nBACKUP\r\nLocal backup is under Caregiver Settings. The website Administrator can also create a server backup before replacing a computer.\r\n\r\nBACKUP & RECOVERY\r\nCaregiver Settings → Recovery can create a backup now, browse local backups, restore a selected backup, and control automatic daily backups. David keeps the newest automatic backups according to the caregiver retention setting. Before a restore, David creates a safety backup of the current data. Each successful installer also keeps the previous David.exe as a last-known-good program for program-only rollback.\r\n\r\nACCESSIBILITY\r\nCaregiver Settings can use larger text, full-screen alerts, and full-screen home.\r\n\r\nMOVING TO A NEW COMPUTER\r\nCreate a server backup, install David on the replacement PC, connect it with a new setup code, then restore from Administrator → Backups.";
		Controls.Add(richTextBox);
		Button button = Ui.Button("OPEN WEBSITE HELP", Theme.Blue, 9);
		button.SetBounds(506, 595, 210, 42);
		Controls.Add(button);
		button.Click += (object param0, EventArgs param1) =>
		{
			try
			{
				Process.Start(new ProcessStartInfo
				{
					FileName = "https://david.forgegather.net/help.html",
					UseShellExecute = true
				});
			}
			catch
			{
			}
		};
	}
}
