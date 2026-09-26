using System;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.Windows.Forms;

namespace DavidCompanion;

public static class Ui
{
	public static GraphicsPath RoundPath(Rectangle r, int radius)
	{
		GraphicsPath graphicsPath = new GraphicsPath();
		int num = Math.Max(2, radius * 2);
		graphicsPath.AddArc(r.X, r.Y, num, num, 180f, 90f);
		graphicsPath.AddArc(r.Right - num, r.Y, num, num, 270f, 90f);
		graphicsPath.AddArc(r.Right - num, r.Bottom - num, num, num, 0f, 90f);
		graphicsPath.AddArc(r.X, r.Bottom - num, num, num, 90f, 90f);
		graphicsPath.CloseFigure();
		return graphicsPath;
	}

	public static Button Button(string text, Color back, int fontSize)
	{
		RoundButton roundButton = new RoundButton();
		roundButton.Text = text;
		roundButton.Font = new Font("Segoe UI", fontSize, FontStyle.Bold);
		roundButton.BackColor = back;
		roundButton.ForeColor = Color.White;
		roundButton.FlatAppearance.BorderSize = 0;
		roundButton.FlatAppearance.MouseOverBackColor = ControlPaint.Light(back, 0.08f);
		roundButton.FlatAppearance.MouseDownBackColor = ControlPaint.Dark(back, 0.08f);
		roundButton.Padding = new Padding(4, 0, 4, 0);
		return roundButton;
	}

	public static Label Label(string text, int size, bool bold, Color color)
	{
		return new Label
		{
			Text = text,
			Font = new Font("Segoe UI", size, bold ? FontStyle.Bold : FontStyle.Regular),
			ForeColor = color,
			BackColor = Color.Transparent,
			UseCompatibleTextRendering = true
		};
	}

	public static RoundedPanel Card(Color back)
	{
		return new RoundedPanel
		{
			BackColor = back,
			Radius = 22,
			BorderColor = Theme.Border,
			BorderWidth = 1
		};
	}

	public static Label Pill(string text, Color back, Color fore)
	{
		Label label = Label(text, 8, bold: true, fore);
		label.TextAlign = ContentAlignment.MiddleCenter;
		label.BackColor = back;
		return label;
	}
}
