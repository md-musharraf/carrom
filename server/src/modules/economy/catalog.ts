/**
 * Server copy of the shop catalogue and arena ladder. Prices and striker power must match the
 * Android client's ShopRepository; the server's copy is the one that is enforced.
 */
export interface CatalogItem {
  id: string;
  kind: "striker" | "board";
  name: string;
  price: number;
  powerMultiplier?: number;
}

export const CATALOG: readonly CatalogItem[] = [
  { id: "classic_ivory", kind: "striker", name: "Classic Ivory", price: 0, powerMultiplier: 1.0 },
  { id: "ruby_emperor", kind: "striker", name: "Ruby Emperor", price: 600, powerMultiplier: 1.15 },
  { id: "emerald_dragon", kind: "striker", name: "Emerald Dragon", price: 1500, powerMultiplier: 1.05 },
  { id: "solar_gold", kind: "striker", name: "Solar Gold Sunburst", price: 3000, powerMultiplier: 1.25 },
  { id: "cyber_neon", kind: "striker", name: "Cyberpunk Synth", price: 5500, powerMultiplier: 1.2 },
  { id: "rose_gold_velvet", kind: "striker", name: "Rose Diamond Luxury", price: 10000, powerMultiplier: 1.3 },
  { id: "classic_teak", kind: "board", name: "Classic Teakwood", price: 0 },
  { id: "royal_indigo", kind: "board", name: "Royal Sapphire Velvet", price: 900 },
  { id: "emerald_sanctuary", kind: "board", name: "Emerald Casino Felt", price: 1800 },
  { id: "vintage_mahogany", kind: "board", name: "Antique Mahogany", price: 3500 },
  { id: "cyber_matrix", kind: "board", name: "Cyberpunk Neon Arena", price: 7000 },
];

export function catalogItem(id: string): CatalogItem | undefined {
  return CATALOG.find((item) => item.id === id);
}

export function strikerPower(id: string): number {
  return catalogItem(id)?.powerMultiplier ?? 1;
}

export interface Arena {
  id: string;
  name: string;
  entryFee: number;
  minLevel: number;
}

/** Stakes ladder for ranked quick matches: the winner takes both entry fees. */
export const ARENAS: readonly Arena[] = [
  { id: "bronze", name: "Bronze Courtyard", entryFee: 100, minLevel: 1 },
  { id: "silver", name: "Silver Pavilion", entryFee: 500, minLevel: 3 },
  { id: "gold", name: "Golden Durbar", entryFee: 2500, minLevel: 8 },
  { id: "royal", name: "Royal Palace", entryFee: 10000, minLevel: 15 },
];

export function arena(id: string): Arena | undefined {
  return ARENAS.find((a) => a.id === id);
}
