using System;

namespace DavidCompanion;

[Serializable]
public class DavidLicenseDevice
{
	public string id { get; set; }

	public string kind { get; set; }

	public string name { get; set; }

	public string activatedAt { get; set; }

	public string lastSeen { get; set; }
}
