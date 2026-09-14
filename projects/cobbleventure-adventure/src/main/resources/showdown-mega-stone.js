// Native Showdown stones use megaEvolves + a string; Mega Showdown also supplies species maps.
const megaEvolution = typeof stone === "string"
    ? (pokemon.getItem().megaEvolves === species.name ? stone : null)
    : stone[species.name];
