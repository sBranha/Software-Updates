using System;

namespace DavidCompanion;

[Serializable]
public class DavidLicenseResponse
{
	public bool ok { get; set; }

	public bool active { get; set; }

	public string error { get; set; }

	public string licenseToken { get; set; }

	public DavidLicenseInfo license { get; set; }

	public DavidLicenseDevice device { get; set; }

	public string serverTime { get; set; }

	public bool temporary { get; set; }

	public bool transportFailure { get; set; }

	public bool serviceSuspended { get; set; }

	public string serviceMode { get; set; }

	public string supportUrl { get; set; }
}
