# Local radar analysis — development foundation

NewsWire v0.8.1 starts with a local-only NEXRAD Level II archive inspector. The initial module validates archive identity, extracts station and volume timestamp, and explicitly marks data as **not decoded**. It is **not** a Level II moment decoder, storm tracker, rotation detector, or alerting engine.

Next: implement strict record framing, compression handling, radial/moment decoding, georeferencing, temporal comparisons, and reproducible tests using known NEXRAD fixtures. Keep all processing on the local computer. Fetch source radar data directly from authorized public providers; do not proxy it through a NewsWire server. Validate scan times and stale data before presenting analysis. Never replace official NWS warnings.
