using System;

namespace DavidCompanion;

[Serializable]
public class UpdateManifest
{
	public string version { get; set; }

	public string publishedAt { get; set; }

	public string installerUrl { get; set; }

	public string sha256 { get; set; }

	public string notes { get; set; }
}
