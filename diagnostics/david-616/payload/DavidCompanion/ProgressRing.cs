using System;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.Windows.Forms;

namespace DavidCompanion;

public class ProgressRing : Control
{
	private int value;

	public int Value
	{
		get
		{
			return value;
		}
		set
		{
			this.value = Math.Max(0, Math.Min(100, value));
			Invalidate();
		}
	}

	public ProgressRing()
	{
		SetStyle(ControlStyles.SupportsTransparentBackColor, value: true);
		DoubleBuffered = true;
		Size = new Size(130, 130);
		BackColor = Color.Transparent;
	}

	protected override void OnPaint(PaintEventArgs e)
	{
		base.OnPaint(e);
		e.Graphics.SmoothingMode = SmoothingMode.AntiAlias;
		Rectangle rect = new Rectangle(11, 11, Width - 23, Height - 23);
		using (Pen pen = new Pen(Theme.IsNight ? Color.FromArgb(55, 69, 91) : Color.FromArgb(215, 224, 233), 12f))
		{
			e.Graphics.DrawArc(pen, rect, -90f, 360f);
		}
		using (Pen pen2 = new Pen(Theme.Green, 12f))
		{
			pen2.StartCap = LineCap.Round;
			pen2.EndCap = LineCap.Round;
			e.Graphics.DrawArc(pen2, rect, -90f, 360f * (float)Value / 100f);
		}
		string s = Value + "%";
		using Font font = new Font("Segoe UI", 19f, FontStyle.Bold);
		using Brush brush = new SolidBrush(Theme.Text);
		SizeF sizeF = e.Graphics.MeasureString(s, font);
		e.Graphics.DrawString(s, font, brush, ((float)Width - sizeF.Width) / 2f, ((float)Height - sizeF.Height) / 2f - 3f);
	}
}
