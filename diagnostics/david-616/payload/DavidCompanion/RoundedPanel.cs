using System;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.Windows.Forms;

namespace DavidCompanion;

public class RoundedPanel : Panel
{
	public int Radius = 22;

	public Color BorderColor = Color.Transparent;

	public int BorderWidth;

	public RoundedPanel()
	{
		DoubleBuffered = true;
		EventHandler value = (object param0, EventArgs param1) =>
		{
			UpdateRegion();
		};
		Resize += value;
	}

	private void UpdateRegion()
	{
		if (Width < 2 || Height < 2)
		{
			return;
		}
		using GraphicsPath path = Ui.RoundPath(new Rectangle(0, 0, Width, Height), Radius);
		Region = new Region(path);
	}

	protected override void OnPaint(PaintEventArgs e)
	{
		base.OnPaint(e);
		if (BorderWidth <= 0 || !(BorderColor != Color.Transparent))
		{
			return;
		}
		e.Graphics.SmoothingMode = SmoothingMode.AntiAlias;
		using Pen pen = new Pen(BorderColor, BorderWidth);
		using GraphicsPath path = Ui.RoundPath(new Rectangle(BorderWidth / 2, BorderWidth / 2, Width - BorderWidth, Height - BorderWidth), Radius);
		e.Graphics.DrawPath(pen, path);
	}
}
