import 'package:flutter/material.dart';

/// Symbole für Unterkonten. Im Backend wird nur der Schlüssel gespeichert.
const subaccountIcons = <String, IconData>{
  'savings': Icons.savings,
  'flight': Icons.flight,
  'home': Icons.home,
  'car': Icons.directions_car,
  'gift': Icons.card_giftcard,
  'school': Icons.school,
  'phone': Icons.smartphone,
  'heart': Icons.favorite,
  'shopping': Icons.shopping_bag,
  'pets': Icons.pets,
};

IconData subaccountIcon(String? key) => subaccountIcons[key] ?? Icons.savings;
