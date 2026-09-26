package mc.nala.servux.util.i18n;

import java.util.List;

// Only the English language file ships with NalaServux.
public enum i18nOption
{
	EN_US   ("en_us", "English (US)", List.of("masa", "sakura-ryoko")),
	UNKNOWN ("unknown","UNK: 'xxx' not found", List.of()),
	;

	private final String key;
	private final String translatedName;
	private final List<String> credits;
	private String description;

	i18nOption(String key, String translatedName)
	{
		this(key, translatedName, List.of());
	}

	i18nOption(String key, String translatedName, List<String> credits)
	{
		this.key = key;
		this.translatedName = translatedName;
		this.credits = credits;
		this.description = "";
	}

	public String getKey()
	{
		return this.key;
	}

	public String getTranslatedName()
	{
		if (!this.description.isEmpty())
		{
			return this.description;
		}

		return translatedName;
	}

	public List<String> getCredits()
	{
		return this.credits;
	}

	private i18nOption setDescription(String description)
	{
		this.description = description;
		return this;
	}

	public static i18nOption fromString(String key)
	{
		// Remove file extension, if present.
		if (key.contains(".json"))
		{
			key = key.replace(".json", "");
		}

		for (i18nOption e : values())
		{
			if (e.key.equalsIgnoreCase(key))
			{
				return e;
			}
		}

		// Not Mapped
		return UNKNOWN.setDescription(String.format("Custom (%s)", key));
	}
}
