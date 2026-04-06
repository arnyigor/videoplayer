#!/bin/bash

# Скрипт для генерации Markdown-файла с изменениями в репозитории
# Выводит измененные и новые файлы с их содержимым

OUTPUT_FILE="changes.md"

# Очищаем файл вывода
> "$OUTPUT_FILE"

# Заголовок
echo "# Изменения в проекте" >> "$OUTPUT_FILE"
echo "" >> "$OUTPUT_FILE"
echo "Дата: $(date '+%Y-%m-%d %H:%M:%S')" >> "$OUTPUT_FILE"
echo "Ветка: $(git branch --show-current)" >> "$OUTPUT_FILE"
echo "" >> "$OUTPUT_FILE"

# Список измененных и новых файлов, исключая бинарные
git ls-files -m -o --exclude-standard | grep -E '\.(kt|java|xml|gradle|kts|properties|md|txt|json|yml|yaml)$' | while read -r file; do
    if [ -f "$file" ]; then
        echo "## File: \`$file\`" >> "$OUTPUT_FILE"
        echo '```' >> "$OUTPUT_FILE"
        cat "$file" >> "$OUTPUT_FILE"
        echo '```' >> "$OUTPUT_FILE"
        echo "" >> "$OUTPUT_FILE"
    fi
done

echo "✅ Файл $OUTPUT_FILE создан!"
echo "📊 Количество файлов: $(grep -c '^## File:' "$OUTPUT_FILE")"
echo "📏 Размер файла: $(du -h "$OUTPUT_FILE" | cut -f1)"
